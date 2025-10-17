package com.mas.masServer.dto;

import com.mas.masServer.entity.GroupAuthType;
import com.mas.masServer.entity.RequestStatus;

import lombok.Data;

@Data
public class BecomeManagerRequestResponseDto {
    private Long requestId;
    private Long userId;
    private String emailId;
    private GroupAuthType groupAuthType;
    private RequestStatus status;
    private String requestDescription;
    private String groupName;
}