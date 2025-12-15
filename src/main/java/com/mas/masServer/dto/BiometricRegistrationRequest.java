package com.mas.masServer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BiometricRegistrationRequest {

    @Email
    private String email;

    @NotEmpty
    @Size(min = 5, max = 5)
    private List<String> fingerprints;
}
