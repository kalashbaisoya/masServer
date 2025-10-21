package com.mas.masServer.repository;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.Group;
public interface GroupRepository extends JpaRepository<Group,Long> {

    List<Group> findByStatus(String string);
    
}
