package com.mas.masServer.dto;

import java.time.LocalDate;

import com.mas.masServer.entity.GroupAuthType;
import com.mas.masServer.entity.MembershipStatus;
import lombok.Data;

@Data
public class MembershipResponseDto {
    private Long membershipId;
    // private Long userId;
    private String memberName;
    private String emailId;
    private String groupRoleName;
    private MembershipStatus status;
    private Long groupId;
    private String groupName;
    private GroupAuthType groupAuthType;
    private String managerName;
    private LocalDate createdOn;
}