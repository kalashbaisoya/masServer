package com.mas.masServer.service;


import com.mas.masServer.dto.AddMemberRequestDto;
import com.mas.masServer.dto.CustomMembershipDTO;
import com.mas.masServer.dto.MembershipStatusUpdateRequest;
import com.mas.masServer.entity.Group;
import com.mas.masServer.entity.GroupAuthState;
import com.mas.masServer.entity.GroupAuthType;
import com.mas.masServer.entity.GroupRole;
import com.mas.masServer.entity.IsOnline;
import com.mas.masServer.entity.Membership;
import com.mas.masServer.entity.MembershipStatus;
import com.mas.masServer.entity.User;
import com.mas.masServer.repository.GroupAuthStateRepository;
import com.mas.masServer.repository.GroupRepository;
import com.mas.masServer.repository.GroupRoleRepository;
import com.mas.masServer.repository.MembershipRepository;
import com.mas.masServer.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @Autowired
    private GroupRoleRepository groupRoleRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private GroupRepository groupRepository;

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

    @Transactional
    @PreAuthorize("hasAuthority('GROUP_ROLE_#groupId_GROUP_MANAGER')")
    public String addMember(Long groupId, AddMemberRequestDto request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate user is system role USER
        if (!"USER".equals(user.getRole().getRoleName())) {
            throw new RuntimeException("Only users with USER role can be added as members");
        }

        // Check if user is already a member
        if (membershipRepository.existsByUserAndGroup(user, group)) {
            throw new RuntimeException("User is already a member of this group");
        }

        // Default to MEMBER role if not specified or invalid
        String roleName = request.getGroupRoleName() != null && group.getGroupAuthType() == GroupAuthType.C && "PENALIST".equalsIgnoreCase(request.getGroupRoleName()) ? "PENALIST" : "MEMBER";
        GroupRole groupRole = groupRoleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new RuntimeException("Group role " + roleName + " not found"));

        // Validate role for group type
        if (group.getGroupAuthType() == GroupAuthType.C && !"PENALIST".equals(roleName) && !"MEMBER".equals(roleName)) {
            throw new RuntimeException("Group Type C only allows MEMBER or PENALIST roles");
        }

        // Create membership
        Membership membership = new Membership();
        membership.setUser(user);
        membership.setGroup(group);
        membership.setGroupRole(groupRole);
        membership.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(membership);

        // Update quorumK
        updateQuorumKOnAdd(group, roleName);

        // Notify user
        String emailMessage = "You have been added to group '" + group.getGroupName() + "' as a " + roleName + ".";
        emailService.sendNotification(user.getEmailId(), "Added to Group", emailMessage);

        // Audit
        String gmEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User gmUser = userRepository.findByEmailId(gmEmail)
                .orElseThrow(() -> new RuntimeException("GM not found"));
        auditLogService.log(gmUser.getUserId(), "membership", "add", null, user.getUserId() + ":" + groupId, "Member added by GM");

        return "Member added successfully";
    }

    private void updateQuorumKOnAdd(Group group, String roleName) {
        GroupAuthType type = group.getGroupAuthType();
        if (type == GroupAuthType.B) {
            group.setQuorumK(group.getQuorumK() + 1); // Increase for each MEMBER in B
        } else if (type == GroupAuthType.C && "PENALIST".equals(roleName)) {
            group.setQuorumK(group.getQuorumK() + 1); // Increase for PENALIST in C
        } // D: Set by GM separately; A: No change
        groupRepository.save(group);
    }
}