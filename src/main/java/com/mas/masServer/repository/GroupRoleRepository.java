package com.mas.masServer.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.GroupRole;

public interface GroupRoleRepository extends JpaRepository<GroupRole,Long> {

    Optional<GroupRole> findByRoleName(String string);
    
}
