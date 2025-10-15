package com.mas.masServer.dto;

import lombok.Data;

@Data
public class AddMemberRequestDto {
    private Long userId; // ID of user to add
    private String groupRoleName; // Optional: MEMBER or PANELIST (defaults to MEMBER)
}
