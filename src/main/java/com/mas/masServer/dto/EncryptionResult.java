package com.mas.masServer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EncryptionResult {
    private String encryptedData;
    private String iv;
}
