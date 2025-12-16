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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class WebSocketEventListener {

    @Autowired
    private GroupAuthStateRepository groupAuthStateRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private HeartbeatService heartbeatService;

    @Autowired
    private MembershipService membershipService;

    @Autowired QuorumService quorumService;

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    @Transactional
    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        log.debug("I am in connect event listener ");
        if (event.getUser() == null) {
            log.warn("SessionConnect/Disconnect event with null principal, sessionId={}", event.getMessage().getHeaders().get("simpSessionId"));
            return;
        }
        String username = event.getUser().getName();
        User user = userRepository.findByEmailId(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        log.debug("WebSocket connected for user: {}", username);
        heartbeatService.updateHeartbeat(username);

        List<Membership> memberships = membershipRepository.findByUser(user);
        for (Membership membership : memberships) {
            if (membership.getStatus() != MembershipStatus.SUSPENDED && user.getIsEmailVerified()) {
                GroupAuthState state = groupAuthStateRepository.findById(membership.getMembershipId())
                        .orElseGet(() -> {
                            GroupAuthState newState = new GroupAuthState();
                            newState.setMembership(membership);
                            return newState;
                        });
                state.setIsOnline(IsOnline.Y);
                state.setLastUpdated(LocalDateTime.now());
                groupAuthStateRepository.save(state);

                membershipService.broadcastMembershipStatuses(membership.getGroup().getGroupId());
            }
        }
    }

   @Transactional
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {

        log.debug("[WS-DISCONNECT] handleSessionDisconnect triggered");

        // ----------------------------------------
        // Resolve username safely
        // ----------------------------------------
        if (event.getUser() == null) {
            log.debug("[WS-DISCONNECT] Event user is null — skipping cleanup");
            return;
        }

        String username = event.getUser().getName();
        log.debug("[WS-DISCONNECT] Disconnect detected | username={}", username);

        // ----------------------------------------
        // Heartbeat cleanup
        // ----------------------------------------
        heartbeatService.removeHeartbeat(username);
        log.debug("[WS-DISCONNECT] Heartbeat removed | username={}", username);

        // ----------------------------------------
        // Resolve User entity
        // ----------------------------------------
        User user = userRepository.findByEmailId(username).orElse(null);

        if (user == null) {
            log.debug(
                "[WS-DISCONNECT] User not found in DB — skipping | username={}",
                username
            );
            return;
        }

        log.debug(
            "[WS-DISCONNECT] User resolved | userId={} | emailVerified={}",
            user.getUserId(),
            user.getIsEmailVerified()
        );

        // ----------------------------------------
        // Fetch memberships
        // ----------------------------------------
        List<Membership> memberships = membershipRepository.findByUser(user);

        log.debug(
            "[WS-DISCONNECT] Memberships found | userId={} | count={}",
            user.getUserId(),
            memberships != null ? memberships.size() : 0
        );

        if (memberships == null || memberships.isEmpty()) {
            log.debug("[WS-DISCONNECT] No memberships — nothing to update");
            return;
        }

        // ----------------------------------------
        // Process each membership
        // ----------------------------------------
        for (Membership membership : memberships) {

            if (membership == null) {
                log.debug("[WS-DISCONNECT] Encountered null membership — skipping");
                continue;
            }

            Long groupId = membership.getGroup() != null
                ? membership.getGroup().getGroupId()
                : null;

            log.debug(
                "[WS-DISCONNECT] Processing membership | membershipId={} | groupId={} | status={}",
                membership.getMembershipId(),
                groupId,
                membership.getStatus()
            );

            // Skip suspended or unverified users
            if (membership.getStatus() == MembershipStatus.SUSPENDED) {
                log.debug(
                    "[WS-DISCONNECT] Membership suspended — skipping | membershipId={}",
                    membership.getMembershipId()
                );
                continue;
            }

            if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
                log.debug(
                    "[WS-DISCONNECT] Email not verified — skipping | userId={}",
                    user.getUserId()
                );
                continue;
            }

            // ----------------------------------------
            // Update GroupAuthState
            // ----------------------------------------
            GroupAuthState state = groupAuthStateRepository
                .findById(membership.getMembershipId())
                .orElseGet(() -> {
                    log.debug(
                        "[WS-DISCONNECT] Creating new GroupAuthState | membershipId={}",
                        membership.getMembershipId()
                    );
                    GroupAuthState newState = new GroupAuthState();
                    newState.setMembership(membership);
                    return newState;
                });

            state.setIsOnline(IsOnline.N);
            state.setAuthIntent(false);
            state.setLastUpdated(LocalDateTime.now());

            groupAuthStateRepository.save(state);

            log.debug(
                "[WS-DISCONNECT] GroupAuthState updated | membershipId={} | online=N | authIntent=false",
                membership.getMembershipId()
            );

            // ----------------------------------------
            // ARCHIVE completed sessions (CRITICAL FIX)
            // ----------------------------------------
            if (groupId != null) {
                try {
                    log.debug(
                        "[WS-DISCONNECT] Archiving completed sessions | groupId={}",
                        groupId
                    );

                    quorumService.archiveCompletedSessionsForGroup(groupId,user);

                } catch (Exception ex) {
                    log.error(
                        "[WS-DISCONNECT] Failed to archive sessions | groupId={}",
                        groupId,
                        ex
                    );
                }

                membershipService.broadcastMembershipStatuses(groupId);
                log.debug(
                    "[WS-DISCONNECT] Membership statuses broadcasted | groupId={}",
                    groupId
                );
            } else {
                log.debug(
                    "[WS-DISCONNECT] GroupId is null — skipping archive/broadcast | membershipId={}",
                    membership.getMembershipId()
                );
            }
        }

        log.debug("[WS-DISCONNECT] handleSessionDisconnect completed | username={}", username);
    }

}