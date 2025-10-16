package com.mas.masServer.dto;

import java.util.List;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;  // JWT
    private String message;
    private UserProfileResponse user;
    private List<CustomMembershipDTO> membershipInfo;

}

