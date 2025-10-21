package com.mas.masServer.service;


import com.mas.masServer.dto.AddMemberRequestDto;
import com.mas.masServer.dto.CustomMembershipDTO;
import com.mas.masServer.dto.MembershipResponseDto;
import com.mas.masServer.dto.MembershipStatusResponseDto;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Transactional
    public String updateMembershipStatus(Long groupId, MembershipStatusUpdateRequest request) {
        // Get authenticated user
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        // Find membership
        Membership membership = membershipRepository.findByUserAndGroup(user, group);
        // Verify ownership and active status
        if (membership==null) {
            auditLogService.log(user.getUserId(), "group_auth_state", "status_toggle", null, "Unauthorized", "Access denied");
            throw new RuntimeException("Unauthorized: You do not own this membership");
        }
        if (!MembershipStatus.ACTIVE.equals(membership.getStatus())) {
            auditLogService.log(user.getUserId(), "group_auth_state", "status_toggle", null, "Inactive membership", "Toggle denied");
            throw new RuntimeException("Membership must not be 'SUSPENDED' to toggle status");
        }

        // Find or create GroupAuthState
        GroupAuthState authState = groupAuthStateRepository.findById(membership.getMembershipId()).orElseGet(() -> {
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

        return "Status updated to " + statusStr + " for membership " + membership.getMembershipId();
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


    @PreAuthorize("hasAnyAuthority('GROUP_ROLE_GROUP_MANAGER', 'ROLE_ADMIN')")
    public List<MembershipResponseDto> viewMembershipsByGroupId(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<MembershipStatus> validStatuses = List.of(MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED);
        List<Membership> memberships = membershipRepository.findByGroupAndStatusIn(group, validStatuses);
        if (memberships== null) {
            throw new RuntimeException("No Membership Found For the groupId: "+ groupId);
        }
        List<MembershipResponseDto> response = memberships.stream().map(m -> {
            MembershipResponseDto dto = new MembershipResponseDto();
            dto.setMembershipId(m.getMembershipId());
            // dto.setUserId(m.getUser().getUserId());
            String memberFullName = String.join(" ",
                m.getUser().getFirstName() != null ? m.getUser().getFirstName() : "",
                m.getUser().getMiddleName() != null ? m.getUser().getMiddleName() : "",
                m.getUser().getLastName() != null ? m.getUser().getLastName() : ""
            ).trim();
            dto.setMemberName(memberFullName);
            dto.setEmailId(m.getUser().getEmailId());
            dto.setGroupRoleName(m.getGroupRole().getRoleName());
            dto.setStatus(m.getStatus());
            dto.setGroupId(m.getGroup().getGroupId());
            dto.setGroupName(m.getGroup().getGroupName());
            dto.setGroupAuthType(m.getGroup().getGroupAuthType());
            dto.setCreatedOn(m.getGroup().getDateTime());
            User manager = m.getGroup().getManager();
            String managerFullName = String.join(" ",
                manager.getFirstName() != null ? manager.getFirstName() : "",
                manager.getMiddleName() != null ? manager.getMiddleName() : "",
                manager.getLastName() != null ? manager.getLastName() : ""
            ).trim();
            dto.setManagerName(managerFullName);
            return dto;
        }).collect(Collectors.toList());

        auditLogService.log(currentUser.getUserId(), "membership", "view", null, groupId.toString(), "Viewed memberships for group");

        return response;
    }

    @Transactional
    @PreAuthorize("hasAuthority('GROUP_ROLE_GROUP_MANAGER')")
    public String addMember(Long groupId, List<AddMemberRequestDto> requests) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        String gmEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User gmUser = userRepository.findByEmailId(gmEmail)
                .orElseThrow(() -> new RuntimeException("GM not found"));

        if (!group.getManager().getUserId().equals(gmUser.getUserId())) {
            throw new RuntimeException("Unauthorized: Only GM can add member to group");
        }

        for (AddMemberRequestDto request : requests) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Validate user is system role USER
            // if (!"USER".equals(user.getRole().getRoleName())) {
            //     throw new RuntimeException("Only users with USER role can be added as members");
            // }

            // Check if user is already a member
            if (membershipRepository.existsByUserAndGroup(user, group)) {
                throw new RuntimeException("User " + user.getUserId() + " is already a member of this group");
            }

            // Default to MEMBER role if not specified or invalid
            String roleName = request.getGroupRoleName() != null && group.getGroupAuthType() == GroupAuthType.C && "PANELIST".equalsIgnoreCase(request.getGroupRoleName()) ? "PANELIST" : "MEMBER";
            GroupRole groupRole = groupRoleRepository.findByRoleName(roleName)
                    .orElseThrow(() -> new RuntimeException("Group role " + roleName + " not found"));

            // Validate role for group type
            if (group.getGroupAuthType() == GroupAuthType.C && !"PANELIST".equals(roleName) && !"MEMBER".equals(roleName)) {
                throw new RuntimeException("Group Type C only allows MEMBER or PANELIST roles");
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
            auditLogService.log(gmUser.getUserId(), "membership", "add", null, user.getUserId() + ":" + groupId, "Member added by GM");
        }

        return "Members added successfully";
    }

    private void updateQuorumKOnAdd(Group group, String roleName) {
        GroupAuthType type = group.getGroupAuthType();
        if (type == GroupAuthType.B) {
            group.setQuorumK(group.getQuorumK() + 1); // Increase for each MEMBER in B
        } else if (type == GroupAuthType.C && "PANELIST".equals(roleName)) {
            group.setQuorumK(group.getQuorumK() + 1); // Increase for PANELIST in C
        } // D: Set by GM separately; A: No change
        groupRepository.save(group);
    }

    public List<MembershipResponseDto> viewMyMemberships() {
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Membership> memberships = membershipRepository.findByUser(user);
        List<MembershipResponseDto> response = memberships.stream().map(m -> {
            MembershipResponseDto dto = new MembershipResponseDto();
            dto.setMembershipId(m.getMembershipId());
            // dto.setUserId(m.getUser().getUserId());
            String memberFullName = String.join(" ",
                m.getUser().getFirstName() != null ? m.getUser().getFirstName() : "",
                m.getUser().getMiddleName() != null ? m.getUser().getMiddleName() : "",
                m.getUser().getLastName() != null ? m.getUser().getLastName() : ""
            ).trim();
            dto.setMemberName(memberFullName);
            dto.setEmailId(m.getUser().getEmailId());
            dto.setGroupRoleName(m.getGroupRole().getRoleName());
            dto.setStatus(m.getStatus());
            dto.setGroupId(m.getGroup().getGroupId());
            dto.setGroupName(m.getGroup().getGroupName());
            dto.setGroupAuthType(m.getGroup().getGroupAuthType());
            dto.setCreatedOn(m.getGroup().getDateTime());
            User manager = m.getGroup().getManager();
            String fullName = String.join(" ",
                manager.getFirstName() != null ? manager.getFirstName() : "",
                manager.getMiddleName() != null ? manager.getMiddleName() : "",
                manager.getLastName() != null ? manager.getLastName() : ""
            ).trim();
            dto.setManagerName(fullName);
            return dto;
        }).collect(Collectors.toList());

        auditLogService.log(user.getUserId(), "memberships", "view_my_memberships", null, null, "Viewed own memberships");

        return response;
    }

    @Transactional
    @PreAuthorize("hasAuthority('GROUP_ROLE_GROUP_MANAGER')")
    public String suspendMember(String memberEmailId, Long groupId) {
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User gmUser = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User GM not found"));
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found : Invalid GroupId"));
        // Validate caller is GM of this particular group
        if (!group.getManager().getUserId().equals(gmUser.getUserId())) {
            throw new RuntimeException("Unauthorized: Current User is not the GM of this group");
        }
        User memberUser = userRepository.findByEmailId(memberEmailId)
                .orElseThrow(() -> new RuntimeException("Member User not found: Invalid memberEmailId"));
        Membership membership = membershipRepository.findByUserAndGroup(memberUser,group);

        // Validate membership belongs to group
        if (membership==null) {
            throw new RuntimeException("Membership does not belong to this group");
        }

        // Prevent suspending GM
        if ("GROUP_MANAGER".equals(membership.getGroupRole().getRoleName())) {
            throw new RuntimeException("Cannot suspend the Group Manager");
        }

        // Check current status
        if (membership.getStatus() == MembershipStatus.SUSPENDED) {
            throw new RuntimeException("Member is already suspended");
        }

        membership.setStatus(MembershipStatus.SUSPENDED);
        membershipRepository.save(membership);

        // Update quorumK
        updateQuorumKOnSuspend(group, membership.getGroupRole().getRoleName());

        // Notify user
        emailService.sendNotification(membership.getUser().getEmailId(), "Membership Suspended", 
                "Your membership in group '" + group.getGroupName() + "' has been suspended.");

        // Audit
        auditLogService.log(gmUser.getUserId(), "membership", "suspend", "active", 
                membership.getMembershipId() + ":" + groupId, "Member suspended by GM");

        return "Member suspended successfully";
    }

    @Transactional
    @PreAuthorize("hasAuthority('GROUP_ROLE_GROUP_MANAGER')")
    public String unsuspendMember(String memberEmailId, Long groupId) {
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User gmUser = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User GM not found"));
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found : Invalid GroupId"));
        // Validate caller is GM of this particular group
        if (!group.getManager().getUserId().equals(gmUser.getUserId())) {
            throw new RuntimeException("Unauthorized: Current User is not the GM of this group");
        }
        User memberUser = userRepository.findByEmailId(memberEmailId)
                .orElseThrow(() -> new RuntimeException("Member User not found: Invalid memberEmailId"));
        Membership membership = membershipRepository.findByUserAndGroup(memberUser,group);

        // Validate membership belongs to group
        if (membership==null) {
            throw new RuntimeException("Membership does not belong to this group");
        }

        // Prevent unsuspending GM (though GM should never be suspended)
        if ("GROUP_MANAGER".equals(membership.getGroupRole().getRoleName())) {
            throw new RuntimeException("Cannot unsuspend the Group Manager");
        }

        // Check current status
        if (membership.getStatus() == MembershipStatus.ACTIVE) {
            throw new RuntimeException("Member is already active");
        }

        membership.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(membership);

        // Update quorumK
        updateQuorumKOnUnsuspend(group, membership.getGroupRole().getRoleName());

        // Notify user
        emailService.sendNotification(membership.getUser().getEmailId(), "Membership Restored", 
                "Your membership in group '" + group.getGroupName() + "' has been restored to active.");

        // Audit
        auditLogService.log(gmUser.getUserId(), "membership", "unsuspend", "suspended", 
                membership.getMembershipId() + ":" + groupId, "Member unsuspended by GM");

        return "Member unsuspended successfully";
    }

    private void updateQuorumKOnSuspend(Group group, String roleName) {
        GroupAuthType type = group.getGroupAuthType();
        if (type == GroupAuthType.B) {
            group.setQuorumK(Math.max(0, group.getQuorumK() - 1)); // Decrease for MEMBER in B
        } else if (type == GroupAuthType.C && "PENALIST".equals(roleName)) {
            group.setQuorumK(Math.max(0, group.getQuorumK() - 1)); // Decrease for PENALIST in C
        } // D/A: No change
        groupRepository.save(group);
    }

    private void updateQuorumKOnUnsuspend(Group group, String roleName) {
        GroupAuthType type = group.getGroupAuthType();
        if (type == GroupAuthType.B) {
            group.setQuorumK(group.getQuorumK() + 1); // Increase for MEMBER in B
        } else if (type == GroupAuthType.C && "PENALIST".equals(roleName)) {
            group.setQuorumK(group.getQuorumK() + 1); // Increase for PENALIST in C
        } // D: Set by GM separately; A: No change
        groupRepository.save(group);
    }

    @PreAuthorize("hasAnyAuthority('GROUP_ROLE_GROUP_MANAGER','GROUP_ROLE_MEMBER','GROUP_ROLE_PANELIST')")
    public List<MembershipStatusResponseDto> viewMembershipStatusesByGroup(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Membership member = membershipRepository.findByUserAndGroup(currentUser,group);

        // Validate membership belongs to group
        if (member==null) {
            throw new RuntimeException("Membership does not belong to this group");
        }
        List<GroupAuthState> authStates = groupAuthStateRepository.findByMembershipGroup(group);
        List<MembershipStatusResponseDto> response = authStates.stream()
                .filter(state -> state.getMembership().getStatus() != MembershipStatus.SUSPENDED
                        && state.getMembership().getUser().getIsEmailVerified()) // Exclude suspended and dummy users
                .map(state -> {
                    Membership membership = state.getMembership();
                    MembershipStatusResponseDto dto = new MembershipStatusResponseDto();
                    dto.setUserId(membership.getUser().getUserId());
                    dto.setEmailId(membership.getUser().getEmailId());
                    dto.setGroupRoleName(membership.getGroupRole().getRoleName());
                    dto.setIsOnline(state.getIsOnline());
                    dto.setLastUpdated(state.getLastUpdated());
                    String firstName = membership.getUser().getFirstName();
                    String middleName = membership.getUser().getMiddleName();
                    String lastName = membership.getUser().getLastName();

                    // Safely build full name (ignores null or empty parts)
                    String fullName = String.join(" ", 
                        firstName != null ? firstName.trim() : "", 
                        (middleName != null && !middleName.trim().isEmpty()) ? middleName.trim() : "", 
                        lastName != null ? lastName.trim() : ""
                    ).trim().replaceAll(" +", " ");

                    dto.setUserName(fullName);
                    return dto;
                }).collect(Collectors.toList());

        auditLogService.log(currentUser.getUserId(), "group_auth_state", "view", null, groupId.toString(), "Viewed membership statuses for group");

        return response;
    }

    @Transactional
    @PreAuthorize("hasAuthority('GROUP_ROLE_GROUP_MANAGER')")
    public String removeMember(String memberEmailId, Long groupId) {
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User gmUser = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User GM not found"));
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found : Invalid GroupId"));
        // Validate caller is GM of this particular group
        if (!group.getManager().getUserId().equals(gmUser.getUserId())) {
            throw new RuntimeException("Unauthorized: Current User is not the GM of this group");
        }
        User memberUser = userRepository.findByEmailId(memberEmailId)
                .orElseThrow(() -> new RuntimeException("Member User not found: Invalid memberEmailId"));
        Membership membership = membershipRepository.findByUserAndGroup(memberUser,group);

        // Validate membership belongs to group
        if (membership==null) {
            throw new RuntimeException("Membership does not belong to this group");
        }

        // Prevent removing GM
        if ("GROUP_MANAGER".equals(membership.getGroupRole().getRoleName())) {
            throw new RuntimeException("Cannot remove the Group Manager");
        }

        User originalUser = membership.getUser();
        if (!"USER".equals(originalUser.getRole().getRoleName())) {
            throw new RuntimeException("Only users with USER role can be removed");
        }

        // Create dummy user
        User dummyUser = new User();
        dummyUser.setEmailId(membership.getMembershipId() + "+" + originalUser.getEmailId());
        dummyUser.setContactNumber(membership.getMembershipId() + "+" + originalUser.getContactNumber());
        dummyUser.setFirstName(originalUser.getFirstName());
        dummyUser.setLastName(originalUser.getLastName());
        dummyUser.setRole(originalUser.getRole());
        dummyUser.setIsEmailVerified(false);
        dummyUser.setDateOfBirth(originalUser.getDateOfBirth());
        dummyUser.setPassword("null");
        // Copy other fields if necessary (e.g., password, security questions)
        userRepository.save(dummyUser);

        // Reassign membership to dummy user
        membership.setUser(dummyUser);
        membership.setStatus(MembershipStatus.DELETED);
        membershipRepository.save(membership);

        // Update quorumK
        updateQuorumKOnRemove(group, membership.getGroupRole().getRoleName());

        // Delete any pending GroupRemoveRequest
        // groupRemoveRequestRepository.findByMembership(membership)
        //         .ifPresent(groupRemoveRequestRepository::delete);

        // Notify original user
        emailService.sendNotification(originalUser.getEmailId(), "Removed from Group",
                "You have been removed from group '" + group.getGroupName() + "'.");

        // Audit
        auditLogService.log(gmUser.getUserId(), "membership", "remove", null,
                membership.getMembershipId() + ":" + groupId, "Member removed by GM, reassigned to dummy user");

        return "Member removed successfully";
    }

    private void updateQuorumKOnRemove(Group group, String roleName) {
        GroupAuthType type = group.getGroupAuthType();
        if (type == GroupAuthType.B) {
            group.setQuorumK(Math.max(0, group.getQuorumK() - 1)); // Decrease for MEMBER in B
        } else if (type == GroupAuthType.C && "PENALIST".equals(roleName)) {
            group.setQuorumK(Math.max(0, group.getQuorumK() - 1)); // Decrease for PENALIST in C
        } // D/A: No change
        groupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public void broadcastMembershipStatuses(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        List<GroupAuthState> authStates = groupAuthStateRepository.findByMembershipGroup(group);
        List<MembershipStatusResponseDto> response = authStates.stream()
                .filter(state -> state.getMembership().getStatus() != MembershipStatus.SUSPENDED
                        && state.getMembership().getUser().getIsEmailVerified())
                .map(state -> {
                    Membership membership = state.getMembership();
                    MembershipStatusResponseDto dto = new MembershipStatusResponseDto();
                    // dto.setMembershipId(membership.getMembershipId());
                    dto.setUserId(membership.getUser().getUserId());
                    dto.setEmailId(membership.getUser().getEmailId());
                    dto.setGroupRoleName(membership.getGroupRole().getRoleName());
                    dto.setIsOnline(state.getIsOnline());
                    dto.setLastUpdated(state.getLastUpdated());
                    return dto;
                }).collect(Collectors.toList());

        messagingTemplate.convertAndSend("/topic/group/" + groupId + "/membership-status", response);
    }
}