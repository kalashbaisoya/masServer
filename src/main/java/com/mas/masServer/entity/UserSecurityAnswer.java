package com.mas.masServer.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "user_security_answer")
@Data
public class UserSecurityAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userSecurityAnswerId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    private SecurityQuestion question;

    @Column(nullable = false)
    private String answer;
}