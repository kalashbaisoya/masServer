package com.mas.masServer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog,Long> {
    
}
