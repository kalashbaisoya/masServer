package com.mas.masServer.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String emailId;
    private String password;
    private Long securityQuestionId;
    private String securityAnswer;
}