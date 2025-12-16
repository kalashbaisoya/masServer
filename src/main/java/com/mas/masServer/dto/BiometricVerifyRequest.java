package com.mas.masServer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BiometricVerifyRequest {
    private String email;
    private String templateToVerify;
}

