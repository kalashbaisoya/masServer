package com.mas.masServer.dto;

import java.time.LocalDateTime;

import com.mas.masServer.entity.AuthenticationSessionStatus;
import com.mas.masServer.entity.SignatureStatus;

import lombok.Data;

@Data
public class SignSessionResponse {

    private Long sessionId;

    private SignatureStatus signatureStatus;   // VERIFIED / REJECTED

    private AuthenticationSessionStatus sessionStatus; // ACTIVE / COMPLETED

    private long verifiedCount;

    private long requiredCount;

    private boolean groupUnlocked;

    private LocalDateTime signedAt;

}
