package com.mas.masServer.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_auth_state")
@Data
public class GroupAuthState {
    @Id
    private Long membershipId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IsOnline isOnline; // Y, N

    @Column(nullable = false)
    private boolean authIntent=false; // true = wants to authenticate

    @Column(nullable = false)
    private LocalDateTime lastUpdated;
}