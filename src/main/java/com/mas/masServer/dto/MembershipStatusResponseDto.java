package com.mas.masServer.dto;

import java.time.LocalDateTime;

import com.mas.masServer.entity.IsOnline;

import lombok.Data;

@Data
public class MembershipStatusResponseDto {
    private Long userId;
    private String userName;
    private String emailId;
    private String groupRoleName;
    private IsOnline isOnline;
    private LocalDateTime lastUpdated;
}
