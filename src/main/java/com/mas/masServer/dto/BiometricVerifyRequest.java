package com.mas.masServer.dto;

import lombok.Data;

@Data
public class BiometricVerifyRequest {
    private String email;
    private String templateToVerify;
}

