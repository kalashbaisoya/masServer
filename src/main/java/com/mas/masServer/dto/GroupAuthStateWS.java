package com.mas.masServer.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.mas.masServer.entity.AuthenticationSessionStatus;
import com.mas.masServer.entity.GroupAuthType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupAuthStateWS {

    private Long groupId;

    private Long sessionId;

    private AuthenticationSessionStatus sessionStatus;

    private GroupAuthType authType;

    private int verifiedCount;

    private int requiredCount;

    private boolean groupUnlocked;

    private LocalDateTime expiresAt;

    private String message; // human-readable hint

    private Map<Long, MemberAuthSnapshot> members; // optional
}

