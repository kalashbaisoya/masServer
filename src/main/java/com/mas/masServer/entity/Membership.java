package com.mas.masServer.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "membership", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "group_id"}))
@Data
public class Membership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long membershipId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne
    @JoinColumn(name = "group_role_id", nullable = false)
    private GroupRole groupRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status; // ACTIVE, SUSPENDED
}

