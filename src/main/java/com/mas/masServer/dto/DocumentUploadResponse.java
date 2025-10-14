package com.mas.masServer.dto;


import com.mas.masServer.entity.AccessType;
import lombok.Data;

@Data
public class DocumentUploadResponse {
    private Long documentId;
    private String fileName;
    private String fileType;
    private AccessType accessType;
    private String fileId;
    private String message;
}