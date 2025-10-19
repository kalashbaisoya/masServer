package com.mas.masServer.controller;

import com.mas.masServer.dto.AddMemberRequestDto;
import com.mas.masServer.dto.MembershipResponseDto;
import com.mas.masServer.dto.MembershipStatusResponseDto;
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

    @PostMapping("/{groupId}/status")
    public ResponseEntity<String> toggleOnlineStatus(
            @PathVariable Long groupId,
            @RequestBody MembershipStatusUpdateRequest request) {
        String message = membershipService.updateMembershipStatus(groupId, request);
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

    @GetMapping("/my-memberships")
    public ResponseEntity<List<MembershipResponseDto>> viewMyMemberships() {
        return ResponseEntity.ok(membershipService.viewMyMemberships());
    }

    @PutMapping("/{groupId}/suspend")
    public ResponseEntity<String> suspendMember(@PathVariable Long groupId, @RequestParam String emailId) {
        return ResponseEntity.ok(membershipService.suspendMember(emailId, groupId));
    }

    @PutMapping("/{groupId}/unsuspend")
    public ResponseEntity<String> unsuspendMember(@PathVariable Long groupId, @RequestParam String emailId) {
        return ResponseEntity.ok(membershipService.unsuspendMember(emailId, groupId));
    }

    @DeleteMapping("/{groupId}/remove")
    public ResponseEntity<String> removeMember(@PathVariable Long groupId, @RequestParam String emailId) {
        return ResponseEntity.ok(membershipService.removeMember(emailId, groupId));
    }

    @GetMapping("/memberships-status")
    public ResponseEntity<List<MembershipStatusResponseDto>> viewMembershipStatusesByGroup(@RequestParam Long groupId) {
        return ResponseEntity.ok(membershipService.viewMembershipStatusesByGroup(groupId));
    }
}