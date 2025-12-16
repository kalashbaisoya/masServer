package com.mas.masServer.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.mas.masServer.dto.AuthenticationSessionResponseDTO;
import com.mas.masServer.dto.BiometricVerifyRequest;
import com.mas.masServer.dto.SignSessionResponse;
import com.mas.masServer.entity.AuthenticationSession;
import com.mas.masServer.entity.AuthenticationSessionStatus;
import com.mas.masServer.entity.AuthenticationSignature;
import com.mas.masServer.entity.Group;
import com.mas.masServer.entity.GroupAuthState;
import com.mas.masServer.entity.GroupAuthType;
import com.mas.masServer.entity.IsOnline;
import com.mas.masServer.entity.Membership;
import com.mas.masServer.entity.MembershipStatus;
import com.mas.masServer.entity.SessionLockScope;
import com.mas.masServer.entity.SignatureStatus;
import com.mas.masServer.entity.User;
import com.mas.masServer.repository.AuthenticationSessionRepository;
import com.mas.masServer.repository.AuthenticationSignatureRepository;
import com.mas.masServer.repository.GroupAuthStateRepository;
import com.mas.masServer.repository.GroupRepository;
import com.mas.masServer.repository.MembershipRepository;
import com.mas.masServer.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.transaction.Transactional;

@Service
public class QuorumService {

        private static final Logger logger = LoggerFactory.getLogger(MembershipService.class);

    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupAuthStateRepository groupAuthStateRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthenticationSessionRepository sessionRepository;
    @Autowired private AuthenticationSignatureRepository signatureRepository;
    @Autowired private BiometricService biometricService;
    @Autowired private GroupAuthBroadcaster groupAuthBroadcaster;
    

    @Transactional
    @PreAuthorize("hasAnyAuthority('GROUP_ROLE_GROUP_MANAGER','GROUP_ROLE_MEMBER','GROUP_ROLE_PANELIST')")
    public AuthenticationSessionResponseDTO createSession(Long groupId) {

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        User initiator = getCurrentUser();

        AuthenticationSession s =  findExistingSession(group, initiator).orElse(null);
        if(s!=null){
            return mapToSessionResponse(s);
        }
        
        // session uniqueness 
        // enforceSessionUniqueness(group, initiator);

        //online quorum
        if (!isQuorumSatisfied(group)) {
                throw new IllegalStateException("Online quorum not satisfied");
        }
        AuthenticationSession session = new AuthenticationSession();
        session.setGroup(group);
        session.setInitiator(initiator);
        session.setAuthType(group.getGroupAuthType());
        session.setStatus(AuthenticationSessionStatus.ACTIVE);
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        switch (group.getGroupAuthType()) {
            case A -> {
                session.setLockScope(SessionLockScope.USER);
                session.setRequiredSignatures(1);
            }
            case B -> {
                session.setLockScope(SessionLockScope.GROUP);
                session.setRequiredSignatures(
                    membershipRepository.countByGroupAndStatus(
                    group,
                    MembershipStatus.ACTIVE
                )
                );
            }
            case C -> {
                session.setLockScope(SessionLockScope.GROUP);
                // session.setRequiredSignatures(group.getQuorumK()+1);
                session.setRequiredSignatures(
                    membershipRepository.countByGroupAndStatusAndGroupRole_RoleNameIn(group,
                        MembershipStatus.ACTIVE,
                        List.of("PANELIST", "GROUP_MANAGER")
                ));
            }
            case D -> {
                session.setLockScope(SessionLockScope.GROUP);
                session.setRequiredSignatures(group.getQuorumK());
            }
        }

        AuthenticationSession saved = sessionRepository.save(session);
        groupAuthBroadcaster.broadcast(session);
        // Pre-create signatures
        for (Membership m : resolveEligibleQuorumMembers(group,saved)) {
            AuthenticationSignature sig = new AuthenticationSignature();
            sig.setSession(saved);
            sig.setMembership(m);
            sig.setStatus(SignatureStatus.PENDING);
            signatureRepository.save(sig);
        }

        return mapToSessionResponse(saved);
    }


    private User getCurrentUser(){
        String emailId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailId(emailId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user;
    }

    private List<Membership> resolveEligibleQuorumMembers(Group group, AuthenticationSession session) {

        List<Membership> active =
            membershipRepository.findByGroupAndStatusIn(
                group, List.of(MembershipStatus.ACTIVE)
            );

        return switch (group.getGroupAuthType()) {

            case A -> List.of(
                membershipRepository.findByUserAndGroup(
                    session.getInitiator(), group
                )
            );

            case B -> active;

            case C -> active.stream()
                .filter(m -> {
                    String role = m.getGroupRole().getRoleName();
                    return role.equals("GROUP_MANAGER") || role.equals("PANELIST");
                })
                .toList();

            case D -> groupAuthStateRepository
                .findByMembershipGroupGroupIdAndIsOnline(
                    group.getGroupId(), IsOnline.Y
                )
                .stream()
                .map(GroupAuthState::getMembership)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .toList();
        };
    }

    private boolean isQuorumSatisfied(Group group) {

        List<GroupAuthState> eligibleStates =
            groupAuthStateRepository
                .findByMembershipGroupGroupIdAndIsOnline(
                    group.getGroupId(),
                    IsOnline.Y
                )
                .stream()
                .filter(state ->
                    state.isAuthIntent() &&
                    state.getMembership().getStatus() == MembershipStatus.ACTIVE
                )
                .toList();

        switch (group.getGroupAuthType()) {

            /**
             * TYPE A
             * Individual authentication → no quorum precondition
             */
            case A:
                return true;

            /**
             * TYPE B
             * ALL members + GM must be online AND opted-in
             */
            case B: {
                long requiredMembers =
                    membershipRepository
                        .findByGroupAndStatusIn(
                            group,
                            List.of(MembershipStatus.ACTIVE)
                        )
                        .stream()
                        .filter(m ->
                            "MEMBER".equals(m.getGroupRole().getRoleName())
                        )
                        .count();

                long onlineMembers =
                    eligibleStates.stream()
                        .filter(state ->
                            "MEMBER".equals(
                                state.getMembership()
                                    .getGroupRole()
                                    .getRoleName()
                            )
                        )
                        .count();

                Membership gmMembership =
                    membershipRepository
                        .findByGroupAndGroupRoleRoleName(
                            group,
                            "GROUP_MANAGER"
                        );

                boolean gmEligible =
                    eligibleStates.stream()
                        .anyMatch(state ->
                            state.getMembership()
                                .getMembershipId()
                                .equals(gmMembership.getMembershipId())
                        );

                return onlineMembers == requiredMembers && gmEligible;
            }

            /**
             * TYPE C
             * GM + ALL PANELISTS must be online AND opted-in
             */
            case C: {
                long requiredPanelists =
                    membershipRepository
                        .findByGroupAndStatusIn(
                            group,
                            List.of(MembershipStatus.ACTIVE)
                        )
                        .stream()
                        .filter(m ->
                            "PANELIST".equals(m.getGroupRole().getRoleName())
                        )
                        .count();

                long onlinePanelists =
                    eligibleStates.stream()
                        .filter(state ->
                            "PANELIST".equals(
                                state.getMembership()
                                    .getGroupRole()
                                    .getRoleName()
                            )
                        )
                        .count();

                Membership gmMembership =
                    membershipRepository
                        .findByGroupAndGroupRoleRoleName(
                            group,
                            "GROUP_MANAGER"
                        );

                boolean gmEligible =
                    eligibleStates.stream()
                        .anyMatch(state ->
                            state.getMembership()
                                .getMembershipId()
                                .equals(gmMembership.getMembershipId())
                        );

                return onlinePanelists == requiredPanelists && gmEligible;
            }

            /**
             * TYPE D
             * Any quorumK members online AND opted-in
             */
            case D:
                return eligibleStates.size() >= group.getQuorumK();

            default:
                return false;
        }
    }



    @Transactional
    @PreAuthorize("hasAnyAuthority('GROUP_ROLE_GROUP_MANAGER','GROUP_ROLE_MEMBER','GROUP_ROLE_PANELIST')")
    public SignSessionResponse signSession(
            Long sessionId,
            String biometricTemplateBase64
    ) {
        logger.debug("[signSession] Entered | sessionId={}", sessionId);

        // ------------------------
        // Basic validation
        // ------------------------
        if (sessionId == null) {
            logger.debug("[signSession] sessionId is null");
            throw new IllegalArgumentException("sessionId cannot be null");
        }

        if (biometricTemplateBase64 == null || biometricTemplateBase64.isBlank()) {
            logger.debug("[signSession] biometricTemplateBase64 is null/blank | sessionId={}", sessionId);
            throw new IllegalArgumentException("Biometric template missing");
        }

        // ------------------------
        // Fetch session
        // ------------------------
        AuthenticationSession session =
            sessionRepository.findById(sessionId)
                .orElseThrow(() -> {
                    logger.debug("[signSession] Session not found | sessionId={}", sessionId);
                    return new RuntimeException("Session not found");
                });

        logger.debug(
            "[signSession] Session loaded | id={} status={} authType={} expiresAt={}",
            session.getSessionId(),
            session.getStatus(),
            session.getAuthType(),
            session.getExpiresAt()
        );

        // ------------------------
        // Fetch current user
        // ------------------------
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            logger.debug("[signSession] Current user is null | sessionId={}", sessionId);
            throw new RuntimeException("Unauthenticated user");
        }

        logger.debug(
            "[signSession] Current user | userId={} email={}",
            currentUser.getUserId(),
            currentUser.getEmailId()
        );

        // ------------------------
        // Fetch membership
        // ------------------------
        Membership membership =
            membershipRepository.findByUserAndGroup(currentUser, session.getGroup());

        if (membership == null) {
            logger.debug(
                "[signSession] Membership not found | userId={} groupId={}",
                currentUser.getUserId(),
                session.getGroup().getGroupId()
            );
            throw new RuntimeException("User is not a member of the group");
        }

        logger.debug(
            "[signSession] Membership found | membershipId={}",
            membership.getMembershipId()
        );

        // ------------------------
        // Fetch signature record
        // ------------------------
        AuthenticationSignature sig =
            signatureRepository.findBySessionAndMembership(session, membership)
                .orElseThrow(() -> {
                    logger.debug(
                        "[signSession] Signature not found | sessionId={} membershipId={}",
                        sessionId,
                        membership.getMembershipId()
                    );
                    return new RuntimeException("Not eligible");
                });

        logger.debug(
            "[signSession] Signature loaded | signatureId={} status={}",
            sig.getSignatureId(),
            sig.getStatus()
        );

        // ------------------------
        // 1️⃣ Expiry check
        // ------------------------
        if (session.getExpiresAt() != null &&
            LocalDateTime.now().isAfter(session.getExpiresAt())) {

            logger.debug("[signSession] Session expired | sessionId={}", sessionId);

            session.setStatus(AuthenticationSessionStatus.EXPIRED);
            sessionRepository.save(session);

            return buildSignResponse(session, sig);
        }

        // ------------------------
        // Prevent double-sign
        // ------------------------
        if (sig.getStatus() == SignatureStatus.VERIFIED) {
            logger.debug(
                "[signSession] Signature already verified | signatureId={}",
                sig.getSignatureId()
            );
            return buildSignResponse(session, sig);
        }

        // ------------------------
        // 2️⃣ Biometric verification
        // ------------------------
        logger.debug(
            "[signSession] Verifying biometric | userEmail={} templateLength={}",
            currentUser.getEmailId(),
            biometricTemplateBase64.length()
        );

        boolean verified = biometricService.verifyBiometric(
            new BiometricVerifyRequest(
                currentUser.getEmailId(),
                biometricTemplateBase64
            )
        );

        logger.debug(
            "[signSession] Biometric verification result | verified={}",
            verified
        );

        if (!verified) {

            logger.debug("[signSession] Biometric rejected | sessionId={}", sessionId);

            sig.setStatus(SignatureStatus.REJECTED);
            sig.setSignedAt(LocalDateTime.now());
            signatureRepository.save(sig);

            // ❗ Failure rules
            switch (session.getAuthType()) {
                case A, B, C -> {
                    logger.debug(
                        "[signSession] Cancelling session due to rejection | authType={}",
                        session.getAuthType()
                    );
                    session.setStatus(AuthenticationSessionStatus.CANCELLED);
                }
                case D -> logger.debug(
                    "[signSession] Quorum auth survives rejection | authType=D"
                );
            }

            sessionRepository.save(session);
            return buildSignResponse(session, sig);
        }

        // ------------------------
        // 3️⃣ VERIFIED
        // ------------------------
        logger.debug("[signSession] Signature verified | signatureId={}", sig.getSignatureId());

        sig.setStatus(SignatureStatus.VERIFIED);
        sig.setSignedAt(LocalDateTime.now());
        signatureRepository.save(sig);

        evaluateSessionCompletion(session);

        logger.debug(
            "[signSession] Completed | sessionId={} finalStatus={}",
            sessionId,
            session.getStatus()
        );

        return buildSignResponse(session, sig);
    }



    private void evaluateSessionCompletion(AuthenticationSession session) {
        long verified =
            signatureRepository.countBySessionAndStatus(
                session, SignatureStatus.VERIFIED
            );

        long required = switch (session.getAuthType()) {
            case A -> 1;
            case B, C -> signatureRepository.countBySession(session);
            case D -> session.getRequiredSignatures();
        };

        boolean completed = verified >= required;

        session.setStatus(
            completed
                ? AuthenticationSessionStatus.COMPLETED
                : AuthenticationSessionStatus.ACTIVE
        );

        sessionRepository.save(session);
        groupAuthBroadcaster.broadcast(session);

    }


    private Optional<AuthenticationSession> findExistingSession(
        Group group,
        User initiator
    ) {
        List<AuthenticationSessionStatus> validStatuses = List.of(
                AuthenticationSessionStatus.ACTIVE,
                AuthenticationSessionStatus.COMPLETED
        );

        if (group.getGroupAuthType() == GroupAuthType.A) {
            return sessionRepository
                    .findTopByGroupAndInitiatorAndLockScopeAndStatusInOrderByCreatedAtDesc(
                            group,
                            initiator,
                            SessionLockScope.USER,
                            validStatuses
                    );
        } else {
            return sessionRepository
                    .findTopByGroupAndLockScopeAndStatusInOrderByCreatedAtDesc(
                            group,
                            SessionLockScope.GROUP,
                            validStatuses
                    );
        }
    }




    @PreAuthorize("hasAnyAuthority('GROUP_ROLE_GROUP_MANAGER','GROUP_ROLE_MEMBER','GROUP_ROLE_PANELIST')")
    public boolean isGroupAccessAllowed(Long groupId) {

        User user = getCurrentUser();

        // 1️⃣ Block if any ACTIVE session blocks the user
        List<AuthenticationSession> activeSessions =
            sessionRepository.findByGroup_GroupIdAndStatus(
                groupId,
                AuthenticationSessionStatus.ACTIVE
            );

        for (AuthenticationSession session : activeSessions) {

            if (session.getLockScope() == SessionLockScope.GROUP) {
                return false;
            }

            if (session.getLockScope() == SessionLockScope.USER &&
                session.getInitiator().getUserId().equals(user.getUserId())) {
                return false;
            }
        }

        // 2️⃣ Check for VALID authorization
        boolean hasValidAuthorization;
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        GroupAuthType type = group.getGroupAuthType();

        if (type == GroupAuthType.A) {
            // USER scoped authorization
            hasValidAuthorization =
                sessionRepository.existsByGroup_GroupIdAndInitiator_UserIdAndStatus(
                    groupId,
                    user.getUserId(),
                    AuthenticationSessionStatus.COMPLETED
                );
        } else {
            // GROUP scoped authorization
            hasValidAuthorization =
                sessionRepository.existsByGroupAndStatus(
                    group,
                    AuthenticationSessionStatus.COMPLETED
                );
        }

        return hasValidAuthorization;
    }
 


    private AuthenticationSessionResponseDTO mapToSessionResponse(AuthenticationSession session) {

        AuthenticationSessionResponseDTO dto = new AuthenticationSessionResponseDTO();

        // Session identity
        dto.setSessionId(session.getSessionId());

        // Group info
        dto.setGroupId(session.getGroup().getGroupId());
        dto.setGroupName(session.getGroup().getGroupName());
        dto.setGroupAuthType(session.getGroup().getGroupAuthType());

        // Initiator info
        dto.setInitiatorUserId(session.getInitiator().getUserId());
        // dto.setInitiatorName(
        //     session.getInitiator().getFirstName() + " " +
        //     session.getInitiator().getLastName()
        // );

            String initiatorName = java.util.stream.Stream.of(
                    session.getInitiator().getFirstName(),
                    session.getInitiator().getMiddleName(),
                    session.getInitiator().getLastName())
                    .filter(java.util.Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.joining(" "));
            dto.setInitiatorName(initiatorName);

        // Session state
        dto.setStatus(session.getStatus());
        dto.setLockScope(session.getLockScope());
        dto.setRequiredSignatures(session.getRequiredSignatures());

        // Time info
        dto.setCreatedAt(session.getCreatedAt());
        dto.setExpiresAt(session.getExpiresAt());

        long secondsRemaining = Duration
                .between(LocalDateTime.now(), session.getExpiresAt())
                .getSeconds();

        dto.setSecondsRemaining(Math.max(secondsRemaining, 0));
        dto.setActive(session.getStatus() == AuthenticationSessionStatus.ACTIVE);

        return dto;
    }


    private SignSessionResponse buildSignResponse(AuthenticationSession session,AuthenticationSignature sig) {
        long verified =
            signatureRepository.countBySessionAndStatus(
                session, SignatureStatus.VERIFIED
            );

        long required =
            session.getRequiredSignatures();

        boolean unlocked =
            session.getStatus() == AuthenticationSessionStatus.COMPLETED;

        SignSessionResponse response = new SignSessionResponse();
        response.setSessionId(session.getSessionId());
        response.setSignatureStatus(sig.getStatus());
        response.setSessionStatus(session.getStatus());
        response.setVerifiedCount(verified);
        response.setRequiredCount(required);
        response.setGroupUnlocked(unlocked);
        response.setSignedAt(sig.getSignedAt());

        return response;
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('GROUP_ROLE_GROUP_MANAGER','GROUP_ROLE_MEMBER','GROUP_ROLE_PANELIST')")
    public void updateAuthIntent(Long groupId, boolean intent) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        Membership m = membershipRepository
            .findByUserAndGroup(getCurrentUser(), group);

        GroupAuthState state =
            groupAuthStateRepository.findById(m.getMembershipId())
                .orElseThrow();

        state.setAuthIntent(intent);
        state.setLastUpdated(LocalDateTime.now());
        groupAuthStateRepository.save(state);


        List<AuthenticationSession> activeSessions =
        sessionRepository.findByGroup_GroupIdAndStatus(
            groupId,
            AuthenticationSessionStatus.ACTIVE
        );

        switch (group.getGroupAuthType()) {

            case A:
                // No broadcast for individual authentication
                break;

            case B:
            case C:
            case D:
                if (!activeSessions.isEmpty()) {
                    // Only ONE active session allowed here
                    groupAuthBroadcaster.broadcast(activeSessions.get(0));
                }
                break;
        }
        

    }


    @Transactional
    public void archiveCompletedSessionsForGroup(Long groupId, User currentUser) {

        logger.debug("[ARCHIVE] Starting archiveCompletedSessionsForGroup | groupId={}", groupId);

        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> {
                logger.debug("[ARCHIVE] Group not found | groupId={}", groupId);
                return new RuntimeException("Group not found");
            });

        logger.debug(
            "[ARCHIVE] Group loaded | groupId={} | authType={}",
            group.getGroupId(),
            group.getGroupAuthType()
        );

        // ----------------------------------------
        // Quorum check
        // ----------------------------------------
        boolean quorumSatisfied = isQuorumSatisfied(group);
        logger.debug(
            "[ARCHIVE] Quorum check | groupId={} | quorumSatisfied={}",
            groupId,
            quorumSatisfied
        );

        // If quorum is still satisfied → DO NOT archive
        if (quorumSatisfied && !(group.getGroupAuthType().equals(GroupAuthType.A))) {
            logger.debug(
                "[ARCHIVE] Skipping archive — quorum still satisfied | groupId={}",
                groupId
            );
            return;
        }

        logger.debug(
            "[ARCHIVE] Current user | userId={} | email={}",
            currentUser != null ? currentUser.getUserId() : null,
            currentUser != null ? currentUser.getEmailId() : null
        );

        List<AuthenticationSession> sessionsToArchive;

        // ----------------------------------------
        // Determine sessions to archive
        // ----------------------------------------
        if (group.getGroupAuthType() == GroupAuthType.A) {

            logger.debug(
                "[ARCHIVE] AuthType A — user-specific completed sessions | groupId={} | userId={}",
                groupId,
                currentUser != null ? currentUser.getUserId() : null
            );

            sessionsToArchive =
                sessionRepository.findByGroup_GroupIdAndInitiatorAndStatus(
                    groupId,
                    currentUser,
                    AuthenticationSessionStatus.COMPLETED
                );

        } else {

            logger.debug(
                "[ARCHIVE] AuthType {} — group-wide completed sessions | groupId={}",
                group.getGroupAuthType(),
                groupId
            );

            sessionsToArchive =
                sessionRepository.findByGroup_GroupIdAndStatus(
                    groupId,
                    AuthenticationSessionStatus.COMPLETED
                );
        }

        logger.debug(
            "[ARCHIVE] Completed sessions found | groupId={} | count={}",
            groupId,
            sessionsToArchive != null ? sessionsToArchive.size() : 0
        );

        if (sessionsToArchive == null || sessionsToArchive.isEmpty()) {
            logger.debug(
                "[ARCHIVE] No completed sessions to archive | groupId={}",
                groupId
            );
            return;
        }

        // ----------------------------------------
        // Archive completed sessions
        // ----------------------------------------
        for (AuthenticationSession session : sessionsToArchive) {
            logger.debug(
                "[ARCHIVE] Archiving session | sessionId={} | previousStatus={}",
                session.getSessionId(),
                session.getStatus()
            );
            session.setStatus(AuthenticationSessionStatus.ARCHIVED_COMPLETED);
        }

        sessionRepository.saveAll(sessionsToArchive);

        logger.debug(
            "[ARCHIVE] Archived sessions saved | groupId={} | archivedCount={}",
            groupId,
            sessionsToArchive.size()
        );

        // ----------------------------------------
        // Check active sessions
        // ----------------------------------------
        List<AuthenticationSession> activeSessions =
            sessionRepository.findByGroup_GroupIdAndStatus(
                groupId,
                AuthenticationSessionStatus.ACTIVE
            );

        logger.debug(
            "[ARCHIVE] Active sessions after archive | groupId={} | activeCount={}",
            groupId,
            activeSessions != null ? activeSessions.size() : 0
        );

        // ----------------------------------------
        // Broadcast logic
        // ----------------------------------------
        switch (group.getGroupAuthType()) {

            case A:
                logger.debug(
                    "[ARCHIVE] AuthType A — no broadcast required | groupId={}",
                    groupId
                );
                break;

            case B:
            case C:
            case D:
                if (activeSessions != null && !activeSessions.isEmpty()) {

                    AuthenticationSession activeSession = activeSessions.get(0);

                    logger.debug(
                        "[ARCHIVE] Broadcasting active session | groupId={} | sessionId={}",
                        groupId,
                        activeSession.getSessionId()
                    );

                    // groupAuthBroadcaster.broadcast(activeSession);
                } else {
                    logger.debug(
                        "[ARCHIVE] No active session to broadcast | groupId={}",
                        groupId
                    );
                }
                break;
        }

        logger.debug(
            "[ARCHIVE] Finished archiveCompletedSessionsForGroup | groupId={}",
            groupId
        );
    }



    // private void enforceSessionUniqueness(Group group, User initiator) {

    //     GroupAuthType type = group.getGroupAuthType();

    //     if (type == GroupAuthType.A) {
    //         // Only prevent same user from creating multiple sessions
    //         boolean exists = sessionRepository
    //             .existsByGroupAndInitiatorAndStatusIn(
    //                 group,
    //                 initiator,
    //                 List.of(
    //                     AuthenticationSessionStatus.COMPLETED,
    //                     AuthenticationSessionStatus.ACTIVE
    //                 )
    //             );

    //         if (exists) {
    //             throw new IllegalStateException("You already have an active or authenticated session");
    //         }

    //     } else {
    //         // B, C, D → only one session per group
    //         boolean exists = sessionRepository
    //             .existsByGroupAndStatusIn(
    //                 group,
    //                 List.of(
    //                     AuthenticationSessionStatus.COMPLETED,
    //                     AuthenticationSessionStatus.ACTIVE
    //                 )
    //             );

    //             if (exists) {
    //                 throw new IllegalStateException("Authentication already in progress or completed already");
    //             }
    //         }
    //     }

        // private boolean isQuorumSatisfied(Group group) {
    //     switch (group.getGroupAuthType()) {
    //         case A:
    //             return true; // No quorum required

    //         case B:
    //             // Require all MEMBER + GM online
    //             // long totalMembers = membershipRepository.countByGroupAndGroupRoleRoleName(group, "MEMBER");
    //             long totalMembers = group.getQuorumK();
    //             List<GroupAuthState> onlineStates = groupAuthStateRepository.findByMembershipGroupGroupIdAndIsOnline(group.getGroupId(), IsOnline.Y);

    //             long onlineMembers = onlineStates.stream()
    //                     .filter(state -> "MEMBER".equals(state.getMembership().getGroupRole().getRoleName())
    //                 && state.getMembership().getStatus()==MembershipStatus.ACTIVE)
    //                     .count();

    //             // Check GM online
    //             Membership gmMembership = membershipRepository.findByGroupAndGroupRoleRoleName(group, "GROUP_MANAGER");
    //             boolean gmOnline = groupAuthStateRepository.findByMembershipMembershipIdAndIsOnline(gmMembership.getMembershipId(), IsOnline.Y) != null;

    //             return onlineMembers == totalMembers && gmOnline;

    //         case C:
    //             // Require all PANELIST + GM online
    //             // long totalPanelists = membershipRepository.countByGroupAndGroupRoleRoleName(group, "PANELIST");
    //             long totalPanelists = group.getQuorumK();

    //             onlineStates = groupAuthStateRepository.findByMembershipGroupGroupIdAndIsOnline(group.getGroupId(), IsOnline.Y);

    //             long onlinePanelists = onlineStates.stream()
    //                     .filter(state -> "PANELIST".equals(state.getMembership().getGroupRole().getRoleName()))
    //                     .count();

    //             // Check GM online
    //             gmMembership = membershipRepository.findByGroupAndGroupRoleRoleName(group, "GROUP_MANAGER");
    //             gmOnline = groupAuthStateRepository.findByMembershipMembershipIdAndIsOnline(gmMembership.getMembershipId(), IsOnline.Y) != null;

    //             return onlinePanelists == totalPanelists && gmOnline;

    //         case D:
    //             // Require at least quorumK online (any role)
    //             long onlineCount = groupAuthStateRepository.countByMembershipGroupAndIsOnline(group, IsOnline.Y);
    //             return onlineCount >= group.getQuorumK();

    //         default:
    //             return false;
    //     }
    // }


}


    
