package com.mas.masServer.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "group_table")
@Data
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long groupId;

    @Column(nullable = false)
    private String groupName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupAuthType groupAuthType; // A, B, C, D

    @ManyToOne
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @Column(nullable = false)
    private Integer quorumK = 0;
}

