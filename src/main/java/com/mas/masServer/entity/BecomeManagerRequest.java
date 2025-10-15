package com.mas.masServer.entity;


import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "become_manager_request")
@Data
public class BecomeManagerRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupAuthType groupAuthType; // A, B, C, D

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status; // PENDING, ACCEPTED, REJECTED

    @Column(nullable = false)
    private String requestDescription;

    @Column(nullable = false)
    private String groupName;
}

