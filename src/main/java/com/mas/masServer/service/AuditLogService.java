package com.mas.masServer.service;


import com.mas.masServer.entity.AuditLog;
import com.mas.masServer.entity.User;
import com.mas.masServer.repository.AuditLogRepository;
import com.mas.masServer.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    public void log(Long userId, String tableName, String columnName, String oldValue, String newValue, String action) {

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        AuditLog log = new AuditLog();
        log.setUser(user);
        log.setTableName(tableName);
        log.setColumnName(columnName);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setChangedOn(LocalDateTime.now());
        auditLogRepository.save(log);
    }
}
