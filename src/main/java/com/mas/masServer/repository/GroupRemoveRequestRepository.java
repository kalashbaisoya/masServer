package com.mas.masServer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.Group;
import com.mas.masServer.entity.GroupRemoveRequest;
import com.mas.masServer.entity.RequestStatus;

public interface GroupRemoveRequestRepository extends JpaRepository<GroupRemoveRequest, Long> {

    List<GroupRemoveRequest> findByMembershipMembershipIdAndStatus(Long membershipId, RequestStatus pending);

    List<GroupRemoveRequest> findByMembershipGroupAndStatus(Group group, RequestStatus pending);
}
