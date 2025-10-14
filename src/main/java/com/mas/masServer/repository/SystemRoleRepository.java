package com.mas.masServer.repository;

import com.mas.masServer.entity.SystemRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemRoleRepository extends JpaRepository<SystemRole, Long> {
    SystemRole findByRoleName(String roleName);
}