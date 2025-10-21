package com.mas.masServer.service;


import com.mas.masServer.entity.GroupAuthState;
import com.mas.masServer.entity.IsOnline;
import com.mas.masServer.entity.Membership;
import com.mas.masServer.entity.MembershipStatus;
import com.mas.masServer.entity.User;
import com.mas.masServer.repository.GroupAuthStateRepository;
import com.mas.masServer.repository.MembershipRepository;
import com.mas.masServer.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
public class HeartbeatScheduler {

    @Autowired
    private HeartbeatService heartbeatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private GroupAuthStateRepository groupAuthStateRepository;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private AuditLogService auditLogService;

    @Scheduled(fixedRate = 30000) // Run every 30 seconds
    @Transactional
    public void checkHeartbeats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeoutThreshold = now.minusSeconds(60); // 60-second timeout

        Set<String> usernames = heartbeatService.getUsernames();
        for (String username : usernames) {
            LocalDateTime lastHeartbeat = heartbeatService.getLastHeartbeat(username);
            User user = userRepository.findByEmailId(username).orElse(null);
            if (user == null || !user.getIsEmailVerified()) {
                continue; // Skip dummy users
            }

            List<Membership> memberships = membershipRepository.findByUser(user);
            for (Membership membership : memberships) {
                if (membership.getStatus() != MembershipStatus.SUSPENDED && user.getIsEmailVerified()) {
                    GroupAuthState state = groupAuthStateRepository.findById(membership.getMembershipId())
                            .orElseGet(() -> {
                                GroupAuthState newState = new GroupAuthState();
                                newState.setMembership(membership);
                                return newState;
                            });

                    IsOnline newStatus = (lastHeartbeat != null && lastHeartbeat.isAfter(timeoutThreshold))
                            ? IsOnline.Y
                            : IsOnline.N;

                    if (state.getIsOnline() != newStatus) {
                        state.setIsOnline(newStatus);
                        state.setLastUpdated(now);
                        groupAuthStateRepository.save(state);

                        auditLogService.log(user.getUserId(), "group_auth_state", "update", null,
                                membership.getMembershipId() + ":" + membership.getGroup().getGroupId(),
                                "Online status updated to " + newStatus + " via heartbeat");

                        membershipService.broadcastMembershipStatuses(membership.getGroup().getGroupId());
                    }
                }
            }

            if (lastHeartbeat == null || lastHeartbeat.isBefore(timeoutThreshold)) {
                heartbeatService.removeHeartbeat(username);
            }
        }
    }
}