package com.mas.masServer.repository;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.Group;
public interface GroupRepository extends JpaRepository<Group,Long> {
    
}
