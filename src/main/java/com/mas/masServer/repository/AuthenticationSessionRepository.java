package com.mas.masServer.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.AuthenticationSession;
import com.mas.masServer.entity.AuthenticationSessionStatus;
import com.mas.masServer.entity.Group;
import com.mas.masServer.entity.SessionLockScope;
import com.mas.masServer.entity.User;

// public interface AuthenticationSessionRepository extends JpaRepository<AuthenticationSession,Long> {



//     boolean existsByGroupAndStatusIn(Group group, List<AuthenticationSessionStatus> of);

//     boolean existsByGroupAndInitiatorAndStatusIn(Group group, User initiator, List<AuthenticationSessionStatus> of);

//     AuthenticationSession findByGroup_GroupId(Long groupId);

//     boolean existsByGroup_GroupIdAndStatusIn(Long groupId, AuthenticationSessionStatus completed);

//     List<AuthenticationSession> findByGroup_GroupIdAndStatus(Long groupId, AuthenticationSessionStatus active);

//     boolean existsByGroup_GroupIdAndInitiator_UserIdAndStatus(Long groupId, Long userId,
//             AuthenticationSessionStatus completed);

//     Optional<AuthenticationSession> findActiveByGroup(Long groupId);

//     List<AuthenticationSession> findByGroup_GroupIdAndInitiatorAndStatus(Long groupId, User currentUser,
//             AuthenticationSessionStatus completed);
    
// }

public interface AuthenticationSessionRepository
        extends JpaRepository<AuthenticationSession, Long> {

    // ---- existence checks ----
    boolean existsByGroupAndInitiatorAndStatus(
        Group group,
        User initiator,
        AuthenticationSessionStatus status
    );

    boolean existsByGroupAndStatus(
        Group group,
        AuthenticationSessionStatus status
    );

    boolean existsByGroupAndStatusIn(
        Group group,
        List<AuthenticationSessionStatus> statuses
    );

    boolean existsByGroupAndInitiatorAndStatusIn(
        Group group,
        User initiator,
        List<AuthenticationSessionStatus> statuses
    );

    boolean existsByGroup_GroupIdAndInitiator_UserIdAndStatus(
        Long groupId,
        Long userId,
        AuthenticationSessionStatus status
    );


    List<AuthenticationSession> findByGroup_GroupIdAndStatus(
        Long groupId,
        AuthenticationSessionStatus status
    );

    List<AuthenticationSession> findByGroup_GroupIdAndInitiatorAndStatus(
        Long groupId,
        User initiator,
        AuthenticationSessionStatus status
    );

    List<AuthenticationSession> findByGroup_GroupId(Long groupId);

    Optional<AuthenticationSession>
    findTopByGroupAndInitiatorAndLockScopeAndStatusInOrderByCreatedAtDesc(
            Group group,
            User initiator,
            SessionLockScope lockScope,
            List<AuthenticationSessionStatus> statuses
    );

    Optional<AuthenticationSession>
    findTopByGroupAndLockScopeAndStatusInOrderByCreatedAtDesc(
            Group group,
            SessionLockScope lockScope,
            List<AuthenticationSessionStatus> statuses
    );

}

 