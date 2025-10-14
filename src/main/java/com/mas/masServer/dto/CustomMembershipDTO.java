package com.mas.masServer.dto;

import com.mas.masServer.entity.GroupAuthType;
import com.mas.masServer.entity.MembershipStatus;

import lombok.Data;

@Data
public class CustomMembershipDTO {
    private Long groupId;
    private String groupName;
    private GroupAuthType groupAuthType;
    private String groupRoleName;
    private MembershipStatus status;
}
