package com.mas.masServer.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "authentication_signature",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"session_id", "membership_id"}
    )
)
@Data
public class AuthenticationSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long signatureId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id")
    private AuthenticationSession session;

    @ManyToOne(optional = false)
    @JoinColumn(name = "membership_id")
    private Membership membership;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignatureStatus status;

    private LocalDateTime signedAt;
}

