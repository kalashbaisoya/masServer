package com.mas.masServer.dto;

import lombok.Data;

@Data
public class CustomUserProfileDTO {
    private Long userId;
    private String firstName;
    private String lastName;
    private String emailId;
    private String contactNumber;
    private String systemRole;
    private Boolean isEmailVerified;
}