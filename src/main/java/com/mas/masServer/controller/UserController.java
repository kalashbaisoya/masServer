package com.mas.masServer.controller;


import com.mas.masServer.dto.LoginRequest;
import com.mas.masServer.dto.LoginResponse;
import com.mas.masServer.dto.UserProfileResponse;
// import com.mas.masServer.dto.UserProfileResponse;
import com.mas.masServer.dto.UserRegisterRequest;
import com.mas.masServer.dto.UserRegisterResponse;
import com.mas.masServer.dto.UserUpdateRequest;
import com.mas.masServer.entity.SecurityQuestion;
import com.mas.masServer.service.LoginService;
import com.mas.masServer.service.SecurityQuestionService;
import com.mas.masServer.service.UserService;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private SecurityQuestionService securityQuestionService;

    @Autowired
    private UserService userService;

    @Autowired
    private LoginService loginService;

    @PostMapping("/register")
    public ResponseEntity<UserRegisterResponse> register(
        @RequestPart("image") MultipartFile image,
        @RequestPart("request") UserRegisterRequest request ){
        UserRegisterResponse response = userService.registerUser(request, image);
        // userService.setSecurityAnswers(response.getUserId(),request.getSecurityAnswerRequest());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOTP(@RequestParam String emailId, @RequestParam String otpCode) {
        userService.verifyOTP(emailId, otpCode);
        return ResponseEntity.ok("Registration successful");
    }

    // Existing register and verify-otp...

    /**
     * 2FA Login: Password + Security Answer
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = loginService.login(request);  // Validates password + answer, returns JWT
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get Security Questions
     * @return
     */
    @GetMapping("/security-questions")
    public ResponseEntity<List<SecurityQuestion>> getSecurityQuestions() {
        List<SecurityQuestion> questions = securityQuestionService.getAllSecurityQuestions();
        return ResponseEntity.ok(questions);
    }

    
    /**
     * Generate and send otp 
     */
    @PostMapping("/generateNewOtp")
    public ResponseEntity<String> generateAndSendOTP(@RequestParam String emailId) {
        userService.generateAndSendOTP(emailId);
        return ResponseEntity.ok("OTP Sent to Your email successfully");
    }
    


    /**
     * Get all user profile (admin and GM)
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserProfileResponse>> getAllVerifiedUserProfile() {
        // Assumes authentication checks userId matches JWT principal
        List<UserProfileResponse> profiles = userService.getAllVerifiedUserProfile();
        return ResponseEntity.ok(profiles);
    }

    /**
     * Update user profile (authenticated)
     */
    @PutMapping("/users/{userId}")
    public ResponseEntity<String> updateUserProfile(@PathVariable Long userId, @RequestBody UserUpdateRequest request) {
        userService.updateUserProfile(userId, request);
        return ResponseEntity.ok("Profile updated successfully");
    }


}