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

    @Transactional
    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        String username = event.getUser().getName();
        User user = userRepository.findByEmailId(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

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