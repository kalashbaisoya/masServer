package com.mas.masServer.dto;


import com.mas.masServer.entity.IsOnline;
import lombok.Data;

@Data
public class MembershipStatusUpdateRequest {
    private IsOnline isOnline; // Y or N
}
