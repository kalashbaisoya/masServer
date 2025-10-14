package com.mas.masServer.dto;

import lombok.Data;

@Data
public class DocumentDownloadResponse {
    private String downloadUrl;
    private String message;
}
