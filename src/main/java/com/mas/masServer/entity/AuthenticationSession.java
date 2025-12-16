package com.mas.masServer.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "authentication_session",
    indexes = {
        @Index(name = "idx_auth_group_status", columnList = "group_id, status")
    }
)
@Data
public class AuthenticationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sessionId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(optional = false)
    @JoinColumn(name = "initiator_user_id", nullable = false)
    private User initiator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupAuthType authType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthenticationSessionStatus status;

    /**
     * GROUP → B, C, D
     * USER  → A
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionLockScope lockScope;

    /**
     * B → all members
     * C → GM + panelists
     * D → quorumK
     * A → 1
     */
    @Column(nullable = false)
    private Integer requiredSignatures;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
