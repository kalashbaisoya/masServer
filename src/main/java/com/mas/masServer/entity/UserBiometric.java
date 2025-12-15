package com.mas.masServer.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_biometrics")
@Data
public class UserBiometric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long biometricId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Lob
    @Column(nullable = false)
    private String encryptedTemplate;

    @Column(nullable = false)
    private String iv;

    @Column(nullable = false)
    private LocalDateTime enrolledAt;
}
