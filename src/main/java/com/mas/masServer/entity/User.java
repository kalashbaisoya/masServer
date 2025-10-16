package com.mas.masServer.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Blob;
import java.time.LocalDate;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false)
    private String firstName;

    private String middleName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, unique = true)
    private String emailId;

    @Column(nullable = false, unique = true)
    private String contactNumber;

    @Column(nullable = false)
    private String password;

    @Column
    private String imageName;

    @Column
    private String imageType;

    @Column(nullable = false)
    private Boolean isEmailVerified;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private SystemRole role;

    // Constraint: Only one admin
    @PrePersist
    @PreUpdate
    public void checkAdminConstraint() {
        if (role != null && "ADMIN".equalsIgnoreCase(role.getRoleName())) {
            // Logic to ensure only one admin exists (can be handled in service layer)
        }
    }
}