package com.mas.masServer.dto;

import java.time.LocalDateTime;

import com.mas.masServer.entity.RequestStatus;

import lombok.Data;

@Data
public class GroupJoinRequestResponseDto {
    private Long requestId;
    private Long requestUserId;
    private String requestUserFullName;
    private String requestUserEmailId;
    private Long requestGroupId;
    private String requestGroupName;
    private RequestStatus status;
    private LocalDateTime requestedOn;
    private String requestDescription;
}
