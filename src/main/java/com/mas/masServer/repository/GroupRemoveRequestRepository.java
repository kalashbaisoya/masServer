package com.mas.masServer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.GroupRemoveRequest;

public interface GroupRemoveRequestRepository extends JpaRepository<GroupRemoveRequest, Long> {
    List<GroupRemoveRequest> findByGroupGroupId(Long groupId); // If view remove requests needed later
}
