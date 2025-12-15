package com.mas.masServer.controller;

import jakarta.validation.Valid;

// import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mas.masServer.dto.BiometricRegistrationRequest;
import com.mas.masServer.dto.BiometricVerifyRequest;
import com.mas.masServer.dto.BiometricVerifyResponse;
import com.mas.masServer.service.BiometricService;

@RestController
@RequestMapping("/api")
public class BiometricController {

    // @Autowired private BiometricService biometricService;

    private final BiometricService biometricService;

    public BiometricController(
            BiometricService biometricService
    ) {
        this.biometricService = biometricService;
    }

    @PostMapping("/reg-biom")
    public ResponseEntity<?> registerBiometric(
            @Valid @RequestBody BiometricRegistrationRequest request
    ) {
        biometricService.registerBiometric(request);
        return ResponseEntity.ok("Biometric registered successfully");
    }

    @PostMapping("/verify-biom")
    public ResponseEntity<?> verify(@RequestBody BiometricVerifyRequest request) {

        boolean match = biometricService.verifyBiometric(request);
        return ResponseEntity.ok(new BiometricVerifyResponse(match));
    }
}
