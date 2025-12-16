package com.mas.masServer.dto;

import com.mas.masServer.entity.SignatureStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberAuthSnapshot {

    private boolean online;

    private boolean authIntent;

    private SignatureStatus signatureStatus; // null if not applicable
}
