package com.mas.masServer.controller;

import com.mas.masServer.dto.*;
import com.mas.masServer.service.GroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/group")
public class GroupController {

    @Autowired
    private GroupService groupService;

    //admin
    @PostMapping("/create")
    public ResponseEntity<CreateGroupResponse> createGroup(@RequestBody CreateGroupRequest request) {
        return ResponseEntity.ok(groupService.createGroup(request));
    }

    //admin
    @DeleteMapping("/{groupId}")
    public ResponseEntity<String> deleteGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.deleteGroupByGroupId(groupId));
    }

    //authenticated
    @GetMapping("/viewAll")
    public ResponseEntity<List<GroupResponse>> viewAllActiveGroups() {
        return ResponseEntity.ok(groupService.viewAllGroups());
    }

    //admin
    @PutMapping("/{groupId}/manager")
    public ResponseEntity<String> replaceManager(@PathVariable Long groupId, @RequestBody ReplaceManagerRequest request) {
        System.out.println("Check point : init replaceGM");
        return ResponseEntity.ok(groupService.replaceGroupManager(groupId, request));
    }

    //GM
    @PutMapping("/{groupId}/quorum-k")
    public ResponseEntity<String> setQuorumK(@PathVariable Long groupId, @RequestBody SetQuorumKRequest request) {
        return ResponseEntity.ok(groupService.setQuorumKforGroupD(groupId, request));
    }

   
}