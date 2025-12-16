package com.mas.masServer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mas.masServer.dto.AuthenticationSessionResponseDTO;
import com.mas.masServer.dto.SignSessionResponse;
import com.mas.masServer.dto.SignChallengeRequest;
import com.mas.masServer.service.QuorumService;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    @Autowired
    private QuorumService quorumService;

    /**
     * Create authentication session for a group
     * Works for A / B / C / D
     */
    @PostMapping("/groups/{groupId}/sessions")
    public ResponseEntity<AuthenticationSessionResponseDTO>
    createSession(@PathVariable Long groupId) {

        AuthenticationSessionResponseDTO dto =
            quorumService.createSession(groupId);

        return ResponseEntity.ok(dto);
    }

    /**
     * Sign an authentication session using biometric
     */
    @PutMapping("/sessions/{sessionId}/sign")
    public ResponseEntity<SignSessionResponse>
    signSession(
        @PathVariable Long sessionId,
        @RequestBody SignChallengeRequest payload
    ) {
        SignSessionResponse response =
            quorumService.signSession(
                sessionId,
                payload.getBiometricTemplateBase64()
            );

        return ResponseEntity.ok(response);
    }

    /**
     * Check whether group access is allowed for current user
     */
    @GetMapping("/groups/{groupId}/access")
    public ResponseEntity<Boolean>
    isGroupAccessAllowed(@PathVariable Long groupId) {

        boolean allowed =
            quorumService.isGroupAccessAllowed(groupId);

        return ResponseEntity.ok(allowed);
    }


    /**
     * User explicitly opts in / out of authentication for a group
     */
    @PutMapping("/groups/{groupId}/auth-intent")
    public ResponseEntity<Void> updateAuthIntent(
            @PathVariable Long groupId,
            @RequestParam Boolean isWaiting
    ) {
        quorumService.updateAuthIntent(groupId, isWaiting);
        return ResponseEntity.ok().build();
    }
}
