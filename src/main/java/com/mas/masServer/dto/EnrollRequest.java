package com.mas.masServer.dto;

import lombok.Data;
import java.util.List;

@Data
public class EnrollRequest {
    private List<String> base64Templates;
}

