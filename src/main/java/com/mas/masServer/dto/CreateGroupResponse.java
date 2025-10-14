package com.mas.masServer.dto;

import com.mas.masServer.entity.GroupAuthType;

import lombok.Data;

@Data
public class CreateGroupResponse {
    private Long groupId;
    private String groupName;
    private String message;
    private GroupAuthType groupAuthType;
    private String managerName;

}
