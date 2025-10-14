// SecurityAnswerRequest (for setting answers)
package com.mas.masServer.dto;

import lombok.Data;

@Data
public class SecurityAnswerRequest {
    private Long questionId;
    private String answer;  // Hashed/encrypted in service
}