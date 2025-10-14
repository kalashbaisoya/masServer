package com.mas.masServer.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "group_role")
@Data
public class GroupRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long groupRoleId;

    @Column(nullable = false, unique = true)
    private String roleName; // GROUP_MANAGER, MEMBER, PENALIST
}