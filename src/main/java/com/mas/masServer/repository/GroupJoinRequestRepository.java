package com.mas.masServer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.GroupJoinRequest;
import com.mas.masServer.entity.RequestStatus;
import com.mas.masServer.entity.User;

public interface GroupJoinRequestRepository extends JpaRepository<GroupJoinRequest, Long> {
    List<GroupJoinRequest> findByGroupGroupId(Long groupId);

    List<GroupJoinRequest> findByUserAndStatus(User user, RequestStatus pending);
    
}
