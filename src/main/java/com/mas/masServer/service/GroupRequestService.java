package com.mas.masServer.service;

import com.mas.masServer.dto.*;
import com.mas.masServer.entity.*;
import com.mas.masServer.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GroupRequestService {

    @Autowired
    private GroupJoinRequestRepository groupJoinRequestRepository;

    @Autowired
    private BecomeManagerRequestRepository becomeManagerRequestRepository;

    @Autowired
    private GroupRemoveRequestRepository groupRemoveRequestRepository;

    @Autowired
    private UserRepository userRepository;
    

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private GroupRoleRepository groupRoleRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private GroupService groupService; // For creating group on accept become manager

    @Transactional
    public String sendJoinRequest(Long groupId, GroupJoinRequestDto request) {
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        GroupJoinRequest joinRequest = new GroupJoinRequest();
        joinRequest.setUser(user);
        joinRequest.setGroup(group);
        joinRequest.setStatus(RequestStatus.PENDING);
        joinRequest.setRequestedOn(LocalDateTime.now());
        joinRequest.setRequestDescription(request.getRequestDescription());
        groupJoinRequestRepository.save(joinRequest);

        emailService.sendNotification(group.getManager().getEmailId(), "New Join Request", "User " + user.getEmailId() + " requested to join group " + group.getGroupName() + ". Description: " + request.getRequestDescription());

        auditLogService.log(user.getUserId(), "group_join_request", "create", null, joinRequest.getRequestId().toString(), "Join request sent");

        return "Join request sent successfully";
    }

    @Transactional
    public String sendBecomeManagerRequest(BecomeManagerRequestDto request) {
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        BecomeManagerRequest userRequest = new BecomeManagerRequest();
        userRequest.setUser(user);
        userRequest.setGroupAuthType(request.getGroupAuthType());
        userRequest.setStatus(RequestStatus.PENDING);
        userRequest.setRequestDescription(request.getRequestDescription());
        userRequest.setGroupName(request.getGroupName());
        becomeManagerRequestRepository.save(userRequest);

        // Notify admin (find admin user)
        User admin = userRepository.findByRoleRoleName("ADMIN")
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        emailService.sendNotification(admin.getEmailId(), "New Become Manager Request", "User " + user.getEmailId() + " requested to become GM. Auth Type: " + request.getGroupAuthType() + ". Description: " + request.getRequestDescription());

        auditLogService.log(user.getUserId(), "become_manager_request", "create", null, userRequest.getRequestId().toString(), "Become manager request sent");

        return "Become manager request sent successfully";
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('GROUP_ROLE_MEMBER','GROUP_ROLE_PANELIST')")
    public String sendRemoveRequest(Long groupId, GroupRemoveRequestDto request) {
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        Membership membership = membershipRepository.findByUserAndGroup(user, group);
        if (membership == null || !MembershipStatus.ACTIVE.equals(membership.getStatus())) {
            throw new RuntimeException("User is not an active member of this group");
        }

        GroupRemoveRequest removeRequest = new GroupRemoveRequest();
        removeRequest.setMembership(membership);
        removeRequest.setGroup(group);
        removeRequest.setStatus(RequestStatus.PENDING);
        removeRequest.setRequestedOn(LocalDateTime.now());
        removeRequest.setRequestDescription(request.getRequestDescription());
        groupRemoveRequestRepository.save(removeRequest);

        emailService.sendNotification(group.getManager().getEmailId(), "New Removal Request", "User " + user.getEmailId() + " requested removal from group " + group.getGroupName() + ". Description: " + request.getRequestDescription());

        auditLogService.log(user.getUserId(), "group_remove_request", "create", null, removeRequest.getRequestId().toString(), "Remove request sent");

        return "Remove request sent successfully";
    }

    @PreAuthorize("hasAuthority('GROUP_ROLE_GROUP_MANAGER')")
    public List<GroupJoinRequestResponseDto> viewJoinRequests(Long groupId) {
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (!group.getManager().getUserId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized: Only GM can view join requests");
        }

        List<GroupJoinRequest> requests = groupJoinRequestRepository.findByGroupGroupId(groupId);
        return requests.stream().map(r -> {
            GroupJoinRequestResponseDto dto = new GroupJoinRequestResponseDto();
            dto.setRequestId(r.getRequestId());
            dto.setUserId(r.getUser().getUserId());
            dto.setStatus(r.getStatus());
            dto.setRequestedOn(r.getRequestedOn());
            dto.setRequestDescription(r.getRequestDescription());
            return dto;
        }).collect(Collectors.toList());
    }

    public List<BecomeManagerRequestResponseDto> viewBecomeManagerRequests() {
        // Admin only (enforced via security config or check role)
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!"ADMIN".equals(user.getRole().getRoleName())) {
            throw new RuntimeException("Unauthorized: Only admin can view become manager requests");
        }

        List<BecomeManagerRequest> requests = becomeManagerRequestRepository.findAll();
        return requests.stream().map(r -> {
            BecomeManagerRequestResponseDto dto = new BecomeManagerRequestResponseDto();
            dto.setRequestId(r.getRequestId());
            dto.setUserId(r.getUser().getUserId());
            dto.setGroupAuthType(r.getGroupAuthType());
            dto.setStatus(r.getStatus());
            dto.setRequestDescription(r.getRequestDescription());
            dto.setGroupName(r.getGroupName());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public String acceptBecomeManagerRequest(Long requestId) {
        // Admin only (enforced via security config)
        BecomeManagerRequest req = becomeManagerRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (req.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        // Create new group with requester as GM
        CreateGroupRequest createReq = new CreateGroupRequest();
        createReq.setGroupName("New Group for " + req.getUser().getFirstName()); // Auto-name or prompt
        createReq.setGroupAuthType(req.getGroupAuthType());
        createReq.setManagerId(req.getUser().getUserId());
        groupService.createGroup(createReq); // Calls groupService to create

        req.setStatus(RequestStatus.ACCEPTED);
        becomeManagerRequestRepository.save(req);

        emailService.sendNotification(req.getUser().getEmailId(), "Become Manager Request Accepted", "Your request has been accepted. You are now GM of a new group.");

        auditLogService.log(req.getUser().getUserId(), "become_manager_request", "accept", null, req.getRequestId().toString(), "Accepted by admin");

        return "Become manager request accepted and group created";
    }

    @Transactional
    @PreAuthorize("hasAuthority('GROUP_ROLE_#groupId_GROUP_MANAGER')")
    public String acceptJoinRequest(Long groupId, Long requestId) {
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User gmUser = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (!group.getManager().getUserId().equals(gmUser.getUserId())) {
            throw new RuntimeException("Unauthorized: Only GM can accept join requests");
        }

        GroupJoinRequest req = groupJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (req.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        // Add member
        GroupRole memberRole = groupRoleRepository.findByRoleName("MEMBER")
                .orElseThrow(() -> new RuntimeException("Role not found"));
        Membership membership = new Membership();
        membership.setUser(req.getUser());
        membership.setGroup(group);
        membership.setGroupRole(memberRole);
        membership.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(membership);

        // Update quorumK based on type
        updateQuorumKOnAdd(group, memberRole.getRoleName());

        req.setStatus(RequestStatus.ACCEPTED);
        groupJoinRequestRepository.save(req);

        emailService.sendNotification(req.getUser().getEmailId(), "Join Request Accepted", "You have been added to group " + group.getGroupName() + ".");

        auditLogService.log(gmUser.getUserId(), "group_join_request", "accept", null, req.getRequestId().toString(), "Accepted by GM");

        return "Join request accepted and member added";
    }

    @Transactional
    @PreAuthorize("hasAuthority('GROUP_ROLE_#groupId_GROUP_MANAGER')")
    public String acceptRemoveRequest(Long groupId, Long requestId) {
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User gmUser = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (!group.getManager().getUserId().equals(gmUser.getUserId())) {
            throw new RuntimeException("Unauthorized: Only GM can accept remove requests");
        }

        GroupRemoveRequest req = groupRemoveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (req.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        // Remove membership
        membershipRepository.delete(req.getMembership());

        // Update quorumK based on type (decrease if applicable)
        updateQuorumKOnRemove(group, req.getMembership().getGroupRole().getRoleName());

        req.setStatus(RequestStatus.ACCEPTED);
        groupRemoveRequestRepository.save(req);

        emailService.sendNotification(req.getMembership().getUser().getEmailId(), "Removal Request Accepted", "You have been removed from group " + group.getGroupName() + ".");

        auditLogService.log(gmUser.getUserId(), "group_remove_request", "accept", null, req.getRequestId().toString(), "Accepted by GM");

        return "Remove request accepted and member removed";
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

    private void updateQuorumKOnRemove(Group group, String roleName) {
        GroupAuthType type = group.getGroupAuthType();
        if (type == GroupAuthType.B) {
            group.setQuorumK(Math.max(0, group.getQuorumK() - 1)); // Decrease for MEMBER in B
        } else if (type == GroupAuthType.C && "PANELIST".equals(roleName)) {
            group.setQuorumK(Math.max(0, group.getQuorumK() - 1)); // Decrease for PANELIST in C
        } // D/A: No auto-change
        groupRepository.save(group);
    }

    // Updated GroupRequestService with reject methods
    // Add these to the existing GroupRequestService class

    @Transactional
    public String rejectBecomeManagerRequest(Long requestId) {
        // Admin only (enforced via security or role check)
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User admin = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!"ADMIN".equals(admin.getRole().getRoleName())) {
            throw new RuntimeException("Unauthorized: Only admin can reject become manager requests");
        }

        BecomeManagerRequest req = becomeManagerRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (req.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        req.setStatus(RequestStatus.REJECTED);
        becomeManagerRequestRepository.save(req);

        emailService.sendNotification(req.getUser().getEmailId(), "Become Manager Request Rejected", "Your request to become GM has been rejected. Description: " + req.getRequestDescription());

        auditLogService.log(admin.getUserId(), "become_manager_request", "reject", null, req.getRequestId().toString(), "Rejected by admin");

        return "Become manager request rejected";
    }

    @Transactional
    @PreAuthorize("hasAuthority('GROUP_ROLE_#groupId_GROUP_MANAGER')")
    public String rejectJoinRequest(Long groupId, Long requestId) {
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User gmUser = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (!group.getManager().getUserId().equals(gmUser.getUserId())) {
            throw new RuntimeException("Unauthorized: Only GM can reject join requests");
        }

        GroupJoinRequest req = groupJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (req.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        req.setStatus(RequestStatus.REJECTED);
        groupJoinRequestRepository.save(req);

        emailService.sendNotification(req.getUser().getEmailId(), "Join Request Rejected", "Your request to join group " + group.getGroupName() + " has been rejected.");

        auditLogService.log(gmUser.getUserId(), "group_join_request", "reject", null, req.getRequestId().toString(), "Rejected by GM");

        return "Join request rejected";
    }

    @Transactional
    @PreAuthorize("hasAuthority('GROUP_ROLE_#groupId_GROUP_MANAGER')")
    public String rejectRemoveRequest(Long groupId, Long requestId) {
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User gmUser = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (!group.getManager().getUserId().equals(gmUser.getUserId())) {
            throw new RuntimeException("Unauthorized: Only GM can reject remove requests");
        }

        GroupRemoveRequest req = groupRemoveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (req.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        req.setStatus(RequestStatus.REJECTED);
        groupRemoveRequestRepository.save(req);

        emailService.sendNotification(req.getMembership().getUser().getEmailId(), "Removal Request Rejected", "Your request to be removed from group " + group.getGroupName() + " has been rejected.");

        auditLogService.log(gmUser.getUserId(), "group_remove_request", "reject", null, req.getRequestId().toString(), "Rejected by GM");

        return "Remove request rejected";
    }
}