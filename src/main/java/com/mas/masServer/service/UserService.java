package com.mas.masServer.service;

import com.mas.masServer.dto.SecurityAnswerRequest;
import com.mas.masServer.dto.UserProfileResponse;
// import com.mas.masServer.dto.UserProfileResponse;
import com.mas.masServer.dto.UserRegisterRequest;
import com.mas.masServer.dto.UserRegisterResponse;
import com.mas.masServer.dto.UserUpdateRequest;
import com.mas.masServer.entity.OTP;
import com.mas.masServer.entity.SecurityQuestion;
import com.mas.masServer.entity.SystemRole;
import com.mas.masServer.entity.User;
import com.mas.masServer.entity.UserSecurityAnswer;
import com.mas.masServer.repository.OTPRepository;
import com.mas.masServer.repository.SecurityQuestionRepository;
import com.mas.masServer.repository.SystemRoleRepository;
import com.mas.masServer.repository.UserRepository;
import com.mas.masServer.repository.UserSecurityAnswerRepository;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SystemRoleRepository systemRoleRepository;

    @Autowired
    private OTPRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private SecurityQuestionRepository securityQuestionRepository;

    @Autowired
    private UserSecurityAnswerRepository userSecurityAnswerRepository;

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String[] ALLOWED_FILE_TYPES = {"application/pdf", "image/png", "image/jpeg"};

    /**
     * Pre-populate security questions on application startup if the table is empty.
     */
    @PostConstruct
    public void initSystemRole() {
        if (systemRoleRepository.count() == 0) {
            List<String> roleName = Arrays.asList(
                "ADMIN",
                "USER"
            );

            for (String r : roleName) {
                SystemRole role = new SystemRole();
                role.setRoleName(r);
                systemRoleRepository.save(role);
            }
        }
    }

    @Transactional
    public UserRegisterResponse registerUser(UserRegisterRequest request, MultipartFile file) {
        // Check if email or contact number already exists
        if (userRepository.existsByEmailId(request.getEmailId())) {
            throw new RuntimeException("Email already exists");
        }

        System.out.println("Check:1");
        if (userRepository.existsByContactNumber(request.getContactNumber())) {
            throw new RuntimeException("Contact number already exists");
        }
        System.out.println("Check:2");

        if (request.getSecurityAnswerRequest() == null || request.getSecurityAnswerRequest().size() != 3) {
            throw new RuntimeException("Exactly 3 security questions must be provided");
        }
        System.out.println("Check:3");

        // Determine role: ADMIN for first user, USER for others
        SystemRole role = userRepository.count() == 0 ?
                systemRoleRepository.findByRoleName("ADMIN") :
                systemRoleRepository.findByRoleName("USER");

        // Create user
        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setMiddleName(request.getMiddleName());
        user.setLastName(request.getLastName());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setEmailId(request.getEmailId());
        user.setContactNumber(request.getContactNumber());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setIsEmailVerified(false);
        System.out.println("Check:4");

        // // Convert imgFile to Blob and set to user
        // if (imgFile != null && !imgFile.isEmpty()) {
        //     try {
        //         java.sql.Blob imageBlob = new javax.sql.rowset.serial.SerialBlob(imgFile.getBytes());
        //         user.setImage(imageBlob);
        //     } catch (Exception e) {
        //         throw new RuntimeException("Failed to process image file", e);
        //     }
        // } else {
        //     user.setImage(null);
        // }

        // Validate file
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size exceeds 10MB");
        }
        if (!Arrays.asList(ALLOWED_FILE_TYPES).contains(file.getContentType())) {
            throw new RuntimeException("Invalid file type. Allowed types: PDF, PNG, JPEG");
        }

        // Generate unique file ID
        String originalFileName = file.getOriginalFilename();
        String fileExtension = originalFileName != null ? originalFileName.substring(originalFileName.lastIndexOf(".")) : "";
        String fileId = UUID.randomUUID() + fileExtension;

        // Upload to MinIO
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileId)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }

        user.setImageName(fileId);
        user.setImageType(file.getContentType());


        System.out.println("Check:5");

        user = userRepository.save(user);
        System.out.println("Check:6");

        generateAndSendOTP(user.getEmailId());

        // Save security answers
        setSecurityAnswers(user.getUserId(), request.getSecurityAnswerRequest());

        // Audit
        auditLogService.log(user.getUserId(), "users", "register", null, user.getEmailId(), "User registered, pending OTP");
        System.out.println("Check:7");

        UserRegisterResponse response = new UserRegisterResponse();
        response.setUserId(user.getUserId());
        response.setEmailId(user.getEmailId());
        response.setMessage("OTP sent to email. Verify to complete registration.");
        System.out.println("Check:8");

        return response;
    }

    public void verifyOTP(String emailId, String otpCode) {
        User user = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        OTP otp = otpRepository.findByUserAndOtpCodeAndIsUsedFalse(user, otpCode)
                .orElseThrow(() -> new RuntimeException("Invalid or expired OTP"));

        if (otp.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP expired");
        }

        otp.setIsUsed(true);
        otpRepository.save(otp);

        user.setIsEmailVerified(true);
        userRepository.save(user);
    }

    public void generateAndSendOTP(String emailId){
        User user = userRepository.findByEmailId(emailId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Generate and send OTP
        String otpCode = UUID.randomUUID().toString().substring(0, 6);
        OTP otp = new OTP();
        otp.setUser(user);
        otp.setOtpCode(otpCode);
        otp.setExpiryTime(LocalDateTime.now().plusMinutes(10));
        otp.setIsUsed(false);
        otpRepository.save(otp);

        emailService.sendOTP(user.getEmailId(), otpCode);
    }

    // In UserService...
    public UserProfileResponse getUserProfile(Long userId) {
        // find user
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        
                // Generate presigned URL (expires in 10 minutes)
        String fileId = user.getImageName();
        String presignedUrl="";
        if(fileId!=null){
                try {
                presignedUrl = minioClient.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.GET)
                                .bucket(bucketName)
                                .object(fileId)
                                .expiry(10, TimeUnit.MINUTES)
                                .build()
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate presigned URL", e);
            }
        }
        
        // Map to DTO (exclude password)
        UserProfileResponse response = new UserProfileResponse();
        // ... populate fields ...
        response.setSystemRole(user.getRole());
        response.setContactNumber(user.getContactNumber());
        // response.setDateOfBirth(user.getDateOfBirth());
        // response.setMiddleName(user.getMiddleName());
        response.setLastName(user.getLastName());
        // response.setImage(user.getImage());
        response.setFirstName(user.getFirstName());
        response.setEmailId(user.getEmailId());
        response.setUserId(user.getUserId());
        response.setIsEmailVerified(user.getIsEmailVerified());
        response.setImage(presignedUrl);

        return response;
    }

    
    @PreAuthorize("hasAnyAuthority('GROUP_ROLE_GROUP_MANAGER','ROLE_ADMIN')")
    public List<UserProfileResponse> getAllVerifiedUserProfile() {
        List<User> users = userRepository.findByIsEmailVerifiedTrue();
        List<UserProfileResponse> responses = new ArrayList<>();
        for (User user : users) {
            UserProfileResponse resp = new UserProfileResponse();
            resp.setUserId(user.getUserId());
            resp.setFirstName(user.getFirstName());
            resp.setLastName(user.getLastName());
            resp.setEmailId(user.getEmailId());
            resp.setContactNumber(user.getContactNumber());
            resp.setDateOfBirth(user.getDateOfBirth());
            resp.setSystemRole(user.getRole());
            // Convert byte[] to Base64 String for easier frontend handling
            // if (user.getImage() != null) {
            //     try {
            //         byte[] imageBytes = user.getImage();
            //         if (imageBytes.length > 0) {
            //             String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
            //             resp.setImage(base64Image); // DTO should have image as String
            //         } else {
            //             resp.setImage(null);
            //         }
            //     } catch (Exception e) {
            //         System.out.println("Error processing image bytes: " + e.getMessage());
            //         resp.setImage(null);
            //     }
            // } else {
            //     resp.setImage(null);
            // }
            resp.setMiddleName(user.getMiddleName());
            // Add other fields as needed
            responses.add(resp);
        }
        return responses;
    }

    @Transactional
    public void updateUserProfile(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        // Update fields (add validation, e.g., unique contactNumber)
        user.setFirstName(request.getFirstName());
        // ... other fields ...
        userRepository.save(user);
        auditLogService.log(userId, "users", "firstName", null, request.getFirstName(), "Profile updated");
    }

    @Transactional
    public void setSecurityAnswers(Long userId, List<SecurityAnswerRequest> answers) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (answers.size() != 3) {
            throw new RuntimeException("Exactly 3 security answers required");
        }

        if (userSecurityAnswerRepository.existsByUserUserId(userId)) {
            userSecurityAnswerRepository.deleteByUserUserId(userId); // delete any existing answers

        }

        // Check for duplicate question IDs in the request itself
        Set<Long> uniqueQuestionIds = new HashSet<>();
        for (SecurityAnswerRequest ans : answers) {
            if (!uniqueQuestionIds.add(ans.getQuestionId())) {
                throw new RuntimeException("Duplicate question selected in request: " + ans.getQuestionId());
            }
        }

        // Prepare all UserSecurityAnswer entities first
        List<UserSecurityAnswer> userAnswers = new ArrayList<>();
        for (SecurityAnswerRequest ans : answers) {
            SecurityQuestion question = securityQuestionRepository.findById(ans.getQuestionId())
                    .orElseThrow(() -> new RuntimeException("Invalid question ID: " + ans.getQuestionId()));

            UserSecurityAnswer userAnswer = new UserSecurityAnswer();
            userAnswer.setUser(user);
            userAnswer.setQuestion(question);
            userAnswer.setAnswer(passwordEncoder.encode(ans.getAnswer())); // Hash answer
            userAnswers.add(userAnswer);
        }

        // Save all answers in one call
        userSecurityAnswerRepository.saveAll(userAnswers);

        auditLogService.log(userId, "user_security_answer", "answers", null, "Set", "Security answers configured");
    }

}
