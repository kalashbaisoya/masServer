package com.mas.masServer.dto;

import com.mas.masServer.entity.GroupAuthType;

import lombok.Data;

@Data
public class BecomeManagerRequestDto {
    private GroupAuthType groupAuthType;
    private String requestDescription;
    private String groupName;
}
