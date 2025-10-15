package com.mas.masServer.controller;

import com.mas.masServer.dto.AddMemberRequestDto;
import com.mas.masServer.dto.MembershipResponseDto;
import com.mas.masServer.dto.MembershipStatusUpdateRequest;
import com.mas.masServer.service.MembershipService;

import java.util.List;

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

    @PostMapping("/{groupId}/addMember")
    public ResponseEntity<String> addMember(@PathVariable Long groupId, @RequestBody List<AddMemberRequestDto> request) {
        return ResponseEntity.ok(membershipService.addMember(groupId, request));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<MembershipResponseDto>> viewMembershipsByGroupId(@PathVariable Long groupId) {
        return ResponseEntity.ok(membershipService.viewMembershipsByGroupId(groupId));
    }
}