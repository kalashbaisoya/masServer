package com.mas.masServer.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class CreateChallengeResponse {
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private String initiatorName;
        private String initiatorEmail;
        private String challengeId;
        private Integer requiredCount;
        private Integer verifiedCount;

}
