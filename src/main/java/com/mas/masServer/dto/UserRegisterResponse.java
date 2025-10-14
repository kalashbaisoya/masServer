package com.mas.masServer.dto;

import lombok.Data;

@Data
public class UserRegisterResponse {
    private Long userId;
    private String emailId;
    private String message;
}