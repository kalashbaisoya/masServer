package com.mas.masServer.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.AuthenticationSession;
import com.mas.masServer.entity.AuthenticationSignature;
import com.mas.masServer.entity.Membership;
import com.mas.masServer.entity.SignatureStatus;

public interface AuthenticationSignatureRepository extends JpaRepository<AuthenticationSignature,Long> {

    // Spring Data will automatically implement this method
    Optional<AuthenticationSignature> findBySessionAndMembership(
        AuthenticationSession session, 
        Membership membership
    );

    long countBySessionAndStatus(AuthenticationSession session, SignatureStatus status);

    long countBySession(AuthenticationSession session);

    // Prevent double signing
    boolean existsBySessionAndMembershipAndStatus(
        AuthenticationSession session,
        Membership membership,
        SignatureStatus status
    );

    // Future quorum logic (Type D)
    long countBySessionAndStatusIn(
        AuthenticationSession session,
        List<SignatureStatus> statuses
    );


}
