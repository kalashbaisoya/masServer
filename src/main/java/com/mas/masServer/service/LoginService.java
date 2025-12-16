package com.mas.masServer.service;


import com.mas.masServer.dto.CustomMembershipDTO;
import com.mas.masServer.dto.LoginRequest;
import com.mas.masServer.dto.LoginResponse;
import com.mas.masServer.dto.UserProfileResponse;
import com.mas.masServer.entity.User;
import com.mas.masServer.entity.UserSecurityAnswer;
import com.mas.masServer.repository.UserRepository;
import com.mas.masServer.repository.UserSecurityAnswerRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

// import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    @Autowired
    private UserService userService;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSecurityAnswerRepository userSecurityAnswerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String secret;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailId(request.getEmailId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if(!user.getIsEmailVerified()) {
            throw new RuntimeException("Email Not Verified ");
        }

        // Validate password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        // Validate security answer
        UserSecurityAnswer answer = userSecurityAnswerRepository
                .findByUserAndQuestion_questionId(user, request.getSecurityQuestionId())
                .orElseThrow(() -> new RuntimeException("Security question not found"));


        if (!passwordEncoder.matches(request.getSecurityAnswer(), answer.getAnswer())) {
            throw new RuntimeException("Invalid Security Answer");
        }


        // Generate JWT token
        String jwtToken = generateJwtToken(user);

        UserProfileResponse userProfile=userService.getUserProfile(user.getUserId());
        List<CustomMembershipDTO> memInfo = membershipService.getMembershipDetailsByUserId(user.getUserId());
 
        LoginResponse response = new LoginResponse();
        response.setToken(jwtToken);
        response.setMessage("Login successful");
        response.setUser(userProfile);
        response.setMembershipInfo(memInfo);

        return response;
    }

    public boolean isValidUser(String password) {
        String userMail = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailId(userMail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if(!user.getIsEmailVerified()) {
            throw new RuntimeException("Email Not Verified ");
        }

        // Validate password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return false;
        }

        return true;
    }

    private String generateJwtToken(User user) {

    
    SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        // Use a secure random key or load from env/config
    // String secret = "your-256-bit-secret-key-in-base64"; // Must be base64-encoded and 256 bits for HS512
    // byte[] keyBytes = java.util.Base64.getDecoder().decode(secret);
    // SecretKey key = Keys.hmacShaKeyFor(keyBytes);

    return Jwts.builder()
            .subject(user.getEmailId()) 
            .claim("userId", user.getUserId())
            .claim("role", user.getRole().getRoleName())
            .issuedAt(new Date(System.currentTimeMillis()))
            .expiration(new Date(System.currentTimeMillis() + 86400000)) // 24h
            .signWith(key, Jwts.SIG.HS512)
            .compact();
    }

    
}
