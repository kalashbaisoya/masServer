package com.mas.masServer.service;


import com.mas.masServer.dto.CustomMembershipDTO;
import com.mas.masServer.dto.MembershipStatusUpdateRequest;
import com.mas.masServer.entity.GroupAuthState;
import com.mas.masServer.entity.IsOnline;
import com.mas.masServer.entity.Membership;
import com.mas.masServer.entity.MembershipStatus;
import com.mas.masServer.entity.User;
import com.mas.masServer.repository.GroupAuthStateRepository;
import com.mas.masServer.repository.MembershipRepository;
import com.mas.masServer.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MembershipService {

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupAuthStateRepository groupAuthStateRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Transactional
    public String updateMembershipStatus(Long membershipId, MembershipStatusUpdateRequest request) {
        // Get authenticated user
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Find membership
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new RuntimeException("Membership not found"));

        // Verify ownership and active status
        if (!user.getUserId().equals(membership.getUser().getUserId())) {
            auditLogService.log(user.getUserId(), "group_auth_state", "status_toggle", null, "Unauthorized", "Access denied");
            throw new RuntimeException("Unauthorized: You do not own this membership");
        }
        if (!MembershipStatus.ACTIVE.equals(membership.getStatus())) {
            auditLogService.log(user.getUserId(), "group_auth_state", "status_toggle", null, "Inactive membership", "Toggle denied");
            throw new RuntimeException("Membership must not be 'SUSPENDED' to toggle status");
        }

        // Find or create GroupAuthState
        GroupAuthState authState = groupAuthStateRepository.findById(membershipId).orElseGet(() -> {
            GroupAuthState newState = new GroupAuthState();
            newState.setMembership(membership);
            return newState;
        });

        // Update status and timestamp (for audit only)
        IsOnline oldStatus = authState.getIsOnline();
        authState.setIsOnline(request.getIsOnline());
        authState.setLastUpdated(LocalDateTime.now());
        groupAuthStateRepository.save(authState);

        // Log the toggle
        String statusStr = request.getIsOnline().toString();
        String oldStatusStr = (oldStatus != null) ? oldStatus.toString() : "null";
        auditLogService.log(
                user.getUserId(),
                "group_auth_state",
                "isOnline",
                oldStatusStr,
                statusStr,
                "Status toggled from " + oldStatusStr + " to " + statusStr
        );

        return "Status updated to " + statusStr + " for membership " + membershipId;
    }

    public List<CustomMembershipDTO> getMembershipDetailsByUserId(Long userId){
        // Find membership
        User user = userRepository.findById(userId).orElseThrow(()-> new RuntimeException("User Not Found"));

    List<Membership> memberships = membershipRepository.findByUser(user);

    List<CustomMembershipDTO> memInfo = memberships.stream()
        .map(m -> {
            CustomMembershipDTO dto = new CustomMembershipDTO();
            dto.setGroupId(m.getGroup().getGroupId());
            dto.setGroupName(m.getGroup().getGroupName());
            dto.setGroupAuthType(m.getGroup().getGroupAuthType());
            dto.setGroupRoleName(m.getGroupRole().getRoleName());
            dto.setStatus(m.getStatus());
            return dto;
        })
        .collect(Collectors.toList());
        return memInfo;
    }
}