package com.mas.masServer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BiometricVerifyResponse {
    private boolean match;
}
