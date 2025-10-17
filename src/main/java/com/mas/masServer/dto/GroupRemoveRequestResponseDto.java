package com.mas.masServer.dto;


import com.mas.masServer.entity.RequestStatus;
import lombok.Data;

@Data
public class GroupRemoveRequestResponseDto {
    private Long requestId;
    private String reqMemberName;
    private String reqMemberEmailId;
    private Long toGroupId;
    private String groupName;
    private Long membershipId;
    private String groupRoleName;
    private RequestStatus status;
}
