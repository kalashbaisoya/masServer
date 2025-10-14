package com.mas.masServer.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "security_question")
@Data
public class SecurityQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long questionId;

    @Column(nullable = false)
    private String questionText;
}