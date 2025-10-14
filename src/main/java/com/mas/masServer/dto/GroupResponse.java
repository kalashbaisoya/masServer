package com.mas.masServer.dto;

import com.mas.masServer.entity.GroupAuthType;

import lombok.Data;

@Data
public class GroupResponse {
    private Long groupId;
    private String groupName;
    private GroupAuthType groupAuthType;
    private Long managerId;
    private Integer quorumK;
}
