package com.mas.masServer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
// import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String defaultFrom;

    private String defaultSubject="Notification";

    /**
     * Sends OTP to user's email for registration verification.
     */
    public void sendOTP(String toEmail, String otpCode) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(defaultFrom);
            message.setTo(toEmail);
            message.setSubject("OTP for MAS app");
            message.setText("Your OTP is: " + otpCode + ". It is valid for 10 minutes. Do not share it with anyone.");

            mailSender.send(message);
            logger.info("OTP sent successfully to: {}", toEmail);
        } catch (MailException e) {
            logger.error("Failed to send OTP to {}: {}", toEmail, e.getMessage());
            // Optionally, throw a custom exception or integrate with AuditLog for retry/failure tracking
            throw new RuntimeException("Failed to send OTP: " + e.getMessage());
        }
    }

    /**
     * Sends a general notification to the user (e.g., group manager assignment, member addition).
     * 
     * @param toEmail Recipient's email
     * @param subject Custom subject (falls back to default if null)
     * @param message Body of the notification
     */

    public void sendNotification(String toEmail, String subject, String message) {
        try {
            SimpleMailMessage emailMessage = new SimpleMailMessage();
            emailMessage.setFrom(defaultFrom);
            emailMessage.setTo(toEmail);
            emailMessage.setSubject(subject != null ? subject : defaultSubject);
            emailMessage.setText(message);

            mailSender.send(emailMessage);
            logger.info("Notification sent successfully to: {}", toEmail);
        } catch (MailException e) {
            logger.error("Failed to send notification to {}: {}", toEmail, e.getMessage());
            // Optionally, integrate with AuditLogService for tracking
            // auditLogService.log(... "email_failure", ...);
        }
    }

    /**
     * Overloaded method for simple notifications (uses default subject).
     * 
     * @param toEmail Recipient's email
     * @param message Body of the notification
     */
    public void sendNotification(String toEmail, String message) {
        sendNotification(toEmail, null, message);
    }


}