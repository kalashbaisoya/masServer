package com.mas.masServer.service;

import com.mas.masServer.dto.*;
import com.mas.masServer.entity.*;
import com.mas.masServer.repository.*;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
// import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
// @PreAuthorize("hasRole('ADMIN')") // All methods admin-only
public class GroupService {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private GroupRoleRepository groupRoleRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuditLogService auditLogService;

    @PostConstruct
    public void initGroupRoleRepo(){
        if(groupRoleRepository.count()==0){
            List<String> grouproleName = Arrays.asList(
                "GROUP_MANAGER",
                "PENALIST",
                "MEMBER"
            );

            for(String r: grouproleName){
                GroupRole groupRole = new GroupRole();
                groupRole.setRoleName(r);
                groupRoleRepository.save(groupRole);
            }
        }
    }

    @Transactional
    // @PreAuthorize("hasRole('ADMIN')")
    public CreateGroupResponse createGroup(CreateGroupRequest request) {
        // Validate manager exists and is USER role
        User manager = userRepository.findById(request.getManagerId())
                .orElseThrow(() -> new RuntimeException("Manager user not found"));
        if (!"USER".equals(manager.getRole().getRoleName())) {
            throw new RuntimeException("Manager must be a USER role");
        }

        // Create group
        Group group = new Group();
        group.setGroupName(request.getGroupName());
        group.setGroupAuthType(request.getGroupAuthType());
        group.setManager(manager);
        group.setQuorumK(0); // Default
        group = groupRepository.save(group);

        // Add manager to membership as GROUP_MANAGER
        GroupRole gmRole = groupRoleRepository.findByRoleName("GROUP_MANAGER")
                .orElseThrow(() -> new RuntimeException("Group role not found"));
        Membership gmMembership = new Membership();
        gmMembership.setUser(manager);
        gmMembership.setGroup(group);
        gmMembership.setGroupRole(gmRole);
        gmMembership.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(gmMembership);

        // Notify new GM
        String message = "You have been appointed as Group Manager for '" + group.getGroupName() + "' (ID: " + group.getGroupId() + "). Auth Type: " + request.getGroupAuthType();
        emailService.sendNotification(manager.getEmailId(), "New Group Manager Role", message);

        // Audit
        auditLogService.log(manager.getUserId(), "group", "create", null, group.getGroupId().toString(), "Group created by admin");

        CreateGroupResponse response = new CreateGroupResponse();
        response.setGroupId(group.getGroupId());
        response.setGroupName(group.getGroupName());
        response.setMessage("Group created successfully");
        response.setGroupAuthType(group.getGroupAuthType());
        response.setManagerName(group.getManager().getFirstName());
        return response;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteGroupByGroupId(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        // Cascade delete memberships, documents, etc. (assuming DB cascades or soft-delete)
        membershipRepository.deleteByGroup(group);
        groupRepository.delete(group);

        // Notify manager (optional)
        String message = "The group '" + group.getGroupName() + "' (ID: " + groupId + ") has been deleted by admin.";
        emailService.sendNotification(group.getManager().getEmailId(), "Group Deleted", message);

        // Audit
        auditLogService.log(group.getManager().getUserId(), "group", "delete", group.getGroupId().toString(), null, "Group deleted by admin");

        return "Group deleted successfully";
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<GroupResponse> viewAllGroups() {
        List<Group> groups = groupRepository.findAll();
        return groups.stream().map(g -> {
            GroupResponse resp = new GroupResponse();
            resp.setGroupId(g.getGroupId());
            resp.setGroupName(g.getGroupName());
            resp.setGroupAuthType(g.getGroupAuthType());
            resp.setManagerId(g.getManager().getUserId());
            resp.setQuorumK(g.getQuorumK());
            return resp;
        }).collect(Collectors.toList());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public String replaceGroupManager(Long groupId, ReplaceManagerRequest request) {
        // Security: Enforced at controller (ADMIN only)
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        User oldManager = group.getManager();
        User newManager = userRepository.findById(request.getNewManagerId())
                .orElseThrow(() -> new RuntimeException("New manager user not found"));
        if (!"USER".equals(newManager.getRole().getRoleName())) {
            throw new RuntimeException("New manager must be a USER role");
        }

        // Update group
        group.setManager(newManager);
        groupRepository.save(group);

        // Update memberships roles
        GroupRole gmRole = groupRoleRepository.findByRoleName("GROUP_MANAGER")
                .orElseThrow(() -> new RuntimeException("Group role not found"));
        GroupRole memberRole = groupRoleRepository.findByRoleName("MEMBER")
                .orElseThrow(() -> new RuntimeException("Group role not found"));

        // Demote old GM to MEMBER (assume existing membership; update role)
        Membership oldGmMembership = membershipRepository.findByUserAndGroup(oldManager, group);
        if (oldGmMembership == null || !MembershipStatus.ACTIVE.equals(oldGmMembership.getStatus())) {
            auditLogService.log(oldManager.getUserId(), "document", "access_attempt", null, "Denied: Not active member", "Access denied");
            throw new RuntimeException("User is not an active member of this group");
        }
        if (oldGmMembership != null) {
            oldGmMembership.setGroupRole(memberRole);
            membershipRepository.save(oldGmMembership);
        } // If no membership (edge case), optionally create as MEMBER

        // Promote new GM: Check if membership exists
        Membership newGmMembership = membershipRepository.findByUserAndGroup(newManager, group);
        if (newGmMembership == null) {
            // Create new membership if not exists
            newGmMembership = new Membership();
            newGmMembership.setUser(newManager);
            newGmMembership.setGroup(group);
            newGmMembership.setStatus(MembershipStatus.ACTIVE);
        }
        newGmMembership.setGroupRole(gmRole);
        membershipRepository.save(newGmMembership);

        // Notifications
        emailService.sendNotification(oldManager.getEmailId(), "Group Manager Role Removed", "You are no longer the manager of group '" + group.getGroupName() + "'.");
        emailService.sendNotification(newManager.getEmailId(), "New Group Manager Role", "You are now the manager of group '" + group.getGroupName() + "'.");

        // Audit
        auditLogService.log(oldManager.getUserId(), "group", "manager_replace", oldManager.getUserId().toString(), newManager.getUserId().toString(), "GM replaced by admin");

        return "Group manager replaced successfully";
    }

    @Transactional
    @PreAuthorize("hasAuthority('GROUP_ROLE_#groupId_GROUP_MANAGER')")
    public String setQuorumKforGroupD(Long groupId, SetQuorumKRequest request) {
        // Get authenticated user (GM)
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        // Validate caller is GM
        if (!group.getManager().getUserId().equals(currentUser.getUserId())) {
            throw new RuntimeException("Unauthorized: Only the Group Manager can set QuorumK");
        }

        // Validate type D
        if (group.getGroupAuthType() != GroupAuthType.D) {
            throw new RuntimeException("QuorumK can only be set for Group Type D");
        }
        if (request.getQuorumK() < 0) {
            throw new RuntimeException("QuorumK must be non-negative");
        }

        group.setQuorumK(request.getQuorumK());
        groupRepository.save(group);

        // Notify GM (self)
        emailService.sendNotification(currentUser.getEmailId(), "QuorumK Updated", "QuorumK for group '" + group.getGroupName() + "' set to " + request.getQuorumK() + ".");

        // Audit
        auditLogService.log(currentUser.getUserId(), "group", "quorumK", null, request.getQuorumK().toString(), "QuorumK set by GM");

        return "QuorumK set successfully for Group D";
    }
}