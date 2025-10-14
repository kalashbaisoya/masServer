package com.mas.masServer.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Data
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String tableName;

    @Column(nullable = false)
    private String columnName;

    private String oldValue;

    private String newValue;

    @Column(nullable = false)
    private LocalDateTime changedOn;
}
