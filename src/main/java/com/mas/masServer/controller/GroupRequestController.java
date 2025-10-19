package com.mas.masServer.controller;

import com.mas.masServer.dto.*;
import com.mas.masServer.service.GroupRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class GroupRequestController {

    @Autowired
    private GroupRequestService groupRequestService;

    // Send join request (authenticated USER)
    @PostMapping("/groups/{groupId}/join-request")
    public ResponseEntity<String> sendJoinRequest(@PathVariable Long groupId, @RequestBody GroupJoinRequestDto request) {
        return ResponseEntity.ok(groupRequestService.sendJoinRequest(groupId, request));
    }

    // Send become manager request (authenticated USER)
    @PostMapping("/become-manager-request")
    public ResponseEntity<String> sendBecomeManagerRequest(@RequestBody BecomeManagerRequestDto request) {
        return ResponseEntity.ok(groupRequestService.sendBecomeManagerRequest(request));
    }

    // Send remove request (group members)
    @PostMapping("/groups/{groupId}/remove-request")
    public ResponseEntity<String> sendRemoveRequest(@PathVariable Long groupId, @RequestBody GroupRemoveRequestDto request) {
        return ResponseEntity.ok(groupRequestService.sendRemoveRequest(groupId, request));
    }

    // Accept become manager request (admin: creates group)
    @PutMapping("/become-manager-requests/{requestId}/accept")
    public ResponseEntity<String> acceptBecomeManagerRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(groupRequestService.acceptBecomeManagerRequest(requestId));
    }

    // Accept join request (GM: adds member)
    @PutMapping("groups/join-requests/{requestId}/accept")
    public ResponseEntity<String> acceptJoinRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(groupRequestService.acceptJoinRequest(requestId));
    }

    // Accept remove request (GM: removes member)
    @PutMapping("/groups/remove-requests/{requestId}/accept")
    public ResponseEntity<String> acceptRemoveRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(groupRequestService.acceptRemoveRequest(requestId));
    }


    // Reject become manager request (admin)
    @PutMapping("/become-manager-requests/{requestId}/reject")
    public ResponseEntity<String> rejectBecomeManagerRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(groupRequestService.rejectBecomeManagerRequest(requestId));
    }

    // Reject join request (GM)
    @PutMapping("/groups/{groupId}/join-requests/{requestId}/reject")
    public ResponseEntity<String> rejectJoinRequest(@PathVariable Long groupId, @PathVariable Long requestId) {
        return ResponseEntity.ok(groupRequestService.rejectJoinRequest(groupId, requestId));
    }

    // Reject remove request (GM)
    @PutMapping("/groups/{groupId}/remove-requests/{requestId}/reject")
    public ResponseEntity<String> rejectRemoveRequest(@PathVariable Long groupId, @PathVariable Long requestId) {
        return ResponseEntity.ok(groupRequestService.rejectRemoveRequest(groupId, requestId));
    }

    // ALL USERS INCLUDINNG ADMIN 
    @GetMapping("/my-become-manager")
    public ResponseEntity<List<BecomeManagerRequestResponseDto>> viewMyBecomeManagerRequests() {
        return ResponseEntity.ok(groupRequestService.viewMyBecomeManagerRequests());
    }

    // ALL USERS INCLUDINNG ADMIN 
    @GetMapping("/my-join-requests")
    public ResponseEntity<List<GroupJoinRequestResponseDto>> viewMyJoinGroupRequests() {
        return ResponseEntity.ok(groupRequestService.viewMyJoinGroupRequests());
    }

    // ONLY GROUP_MEMBERS
    @GetMapping("/my-remove-requsts")
    public ResponseEntity<List<GroupRemoveRequestResponseDto>> viewMyRemoveFromGroupRequests() {
        return ResponseEntity.ok(groupRequestService.viewMyRemoveFromGroupRequests());
    }

    // Only GM 
    @GetMapping("groups/{groupId}/remove-requests")
    public ResponseEntity<List<GroupRemoveRequestResponseDto>> viewAllRemoveFromGroupRequests(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupRequestService.viewAllRemoveFromGroupRequests(groupId));
    }


    // View all join requests for a group (GM)
    @GetMapping("/groups/{groupId}/join-requests")
    public ResponseEntity<List<GroupJoinRequestResponseDto>> viewAllGroupJoinRequestByGroupId(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupRequestService.viewJoinRequests(groupId));
    }

    // View all become manager requests (admin)
    @GetMapping("/become-manager-requests")
    public ResponseEntity<List<BecomeManagerRequestResponseDto>> viewAllBecomeManagerRequestsToAdmin() {
        return ResponseEntity.ok(groupRequestService.viewBecomeManagerRequests());
    }
}