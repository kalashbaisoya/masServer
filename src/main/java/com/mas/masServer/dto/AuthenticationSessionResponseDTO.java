package com.mas.masServer.dto;

import java.time.LocalDateTime;

import com.mas.masServer.entity.AuthenticationSessionStatus;
import com.mas.masServer.entity.GroupAuthType;
import com.mas.masServer.entity.SessionLockScope;

import lombok.Data;

@Data
public class AuthenticationSessionResponseDTO {

    private Long sessionId;

    /* Group info */
    private Long groupId;
    private String groupName;
    private GroupAuthType groupAuthType;

    /* Initiator info */
    private Long initiatorUserId;
    private String initiatorName;

    /* Session behavior */
    private AuthenticationSessionStatus status;
    private SessionLockScope lockScope;
    private Integer requiredSignatures;

    /* Timing */
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    /* UI helpers */
    private long secondsRemaining;
    private boolean active;
}

