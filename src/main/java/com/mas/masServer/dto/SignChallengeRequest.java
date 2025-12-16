package com.mas.masServer.dto;

import lombok.Data;

@Data
public class SignChallengeRequest {
    private String biometricTemplateBase64;
}
