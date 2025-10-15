package com.mas.masServer.dto;

import com.mas.masServer.entity.MembershipStatus;
import lombok.Data;

@Data
public class MembershipResponseDto {
    private Long membershipId;
    private Long userId;
    private String emailId;
    private String groupRoleName;
    private MembershipStatus status;
}