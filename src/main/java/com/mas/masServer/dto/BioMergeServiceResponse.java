package com.mas.masServer.dto;
import lombok.Data;

@Data
public class BioMergeServiceResponse {
    private boolean success;
    private String mergedTemplate;
    private String message;
}