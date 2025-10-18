package com.mas.masServer.dto;

import com.mas.masServer.entity.AccessType;

import lombok.Data;

@Data
public class DocumentResponseDto {
    private Long documentId;
    private String fileName;
    private String fileType;
    private AccessType accessType;
}
