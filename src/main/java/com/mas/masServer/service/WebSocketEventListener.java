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
        log.debug("I am here in method: handleSessionDisconnect");
        String username = event.getUser().getName();
        heartbeatService.removeHeartbeat(username);

        User user = userRepository.findByEmailId(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Membership> memberships = membershipRepository.findByUser(user);
        for (Membership membership : memberships) {
            if (membership.getStatus() != MembershipStatus.SUSPENDED && user.getIsEmailVerified()) {
                GroupAuthState state = groupAuthStateRepository.findById(membership.getMembershipId())
                        .orElseGet(() -> {
                            GroupAuthState newState = new GroupAuthState();
                            newState.setMembership(membership);
                            return newState;
                        });
                state.setIsOnline(IsOnline.N);
                state.setLastUpdated(LocalDateTime.now());
                groupAuthStateRepository.save(state);

                membershipService.broadcastMembershipStatuses(membership.getGroup().getGroupId());
            }
        }
    }
}