package com.mas.masServer.dto;

import com.mas.masServer.entity.GroupAuthType;

import lombok.Data;

@Data
public class CreateGroupRequest {
    private String groupName;
    private GroupAuthType groupAuthType;
    private Long managerId;
}
