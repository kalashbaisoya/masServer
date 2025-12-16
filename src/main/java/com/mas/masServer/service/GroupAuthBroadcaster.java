package com.mas.masServer.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.mas.masServer.dto.GroupAuthStateWS;
import com.mas.masServer.dto.MemberAuthSnapshot;
import com.mas.masServer.entity.AuthenticationSession;
import com.mas.masServer.entity.AuthenticationSessionStatus;
import com.mas.masServer.entity.AuthenticationSignature;
import com.mas.masServer.entity.GroupAuthState;
import com.mas.masServer.entity.IsOnline;
import com.mas.masServer.entity.SignatureStatus;
import com.mas.masServer.repository.AuthenticationSignatureRepository;
import com.mas.masServer.repository.GroupAuthStateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroupAuthBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final AuthenticationSignatureRepository signatureRepository;
    private final GroupAuthStateRepository groupAuthStateRepository;

    public void broadcast(AuthenticationSession session) {

        Long groupId = session.getGroup().getGroupId();

        long verified =
            signatureRepository.countBySessionAndStatus(
                session, SignatureStatus.VERIFIED
            );

        int required = session.getRequiredSignatures();

        boolean unlocked =
            session.getStatus() == AuthenticationSessionStatus.COMPLETED;

        Map<Long, MemberAuthSnapshot> members = buildMemberSnapshot(groupId, session);

        GroupAuthStateWS payload =
            GroupAuthStateWS.builder()
                .groupId(groupId)
                .sessionId(session.getSessionId())
                .sessionStatus(session.getStatus())
                .authType(session.getAuthType())
                .verifiedCount((int) verified)
                .requiredCount(required)
                .groupUnlocked(unlocked)
                .expiresAt(session.getExpiresAt())
                .members(members)
                .message(resolveMessage(session))
                .build();

        messagingTemplate.convertAndSend(
            "/topic/group/" + groupId + "/auth-state",
            payload
        );
    }

    private String resolveMessage(AuthenticationSession session) {
        return switch (session.getStatus()) {
            case ACTIVE -> "Authentication in progress";
            case COMPLETED -> "Group unlocked";
            case CANCELLED -> "Authentication cancelled";
            case EXPIRED -> "Authentication expired";
            default -> "";
        };
    }

    private Map<Long, MemberAuthSnapshot> buildMemberSnapshot(
            Long groupId,
            AuthenticationSession session) {

        Map<Long, MemberAuthSnapshot> map = new HashMap<>();

        List<GroupAuthState> states =
            groupAuthStateRepository
                .findByMembershipGroupGroupId(groupId);

        for (GroupAuthState state : states) {

            SignatureStatus sigStatus =
                signatureRepository
                    .findBySessionAndMembership(
                        session,
                        state.getMembership()
                    )
                    .map(AuthenticationSignature::getStatus)
                    .orElse(null);

            map.put(
                state.getMembership().getMembershipId(),
                MemberAuthSnapshot.builder()
                    .online(state.getIsOnline() == IsOnline.Y)
                    .authIntent(state.isAuthIntent())
                    .signatureStatus(sigStatus)
                    .build()
            );
        }
        return map;
    }
}

