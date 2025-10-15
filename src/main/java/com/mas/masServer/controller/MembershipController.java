package com.mas.masServer.controller;

import com.mas.masServer.dto.MembershipStatusUpdateRequest;
import com.mas.masServer.service.MembershipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {

    @Autowired
    private MembershipService membershipService;

    @PostMapping("/{membershipId}/status")
    public ResponseEntity<String> updateOnlineStatus(
            @PathVariable Long membershipId,
            @RequestBody MembershipStatusUpdateRequest request) {
        String message = membershipService.updateMembershipStatus(membershipId, request);
        return ResponseEntity.ok(message);
    }
}