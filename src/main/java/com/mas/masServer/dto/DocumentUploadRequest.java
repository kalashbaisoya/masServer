package com.mas.masServer.dto;

import com.mas.masServer.entity.AccessType;
import lombok.Data;

@Data
public class DocumentUploadRequest {
    private Long groupId;
    private AccessType accessType;
}