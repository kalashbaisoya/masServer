package com.mas.masServer.dto;

import java.time.LocalDateTime;

import com.mas.masServer.entity.RequestStatus;

import lombok.Data;

@Data
public class GroupJoinRequestResponseDto {
    private Long requestId;
    private Long userId;
    private RequestStatus status;
    private LocalDateTime requestedOn;
    private String requestDescription;
}
