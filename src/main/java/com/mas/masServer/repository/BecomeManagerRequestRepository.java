package com.mas.masServer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.BecomeManagerRequest;
import com.mas.masServer.entity.RequestStatus;
import com.mas.masServer.entity.User;

public interface BecomeManagerRequestRepository extends JpaRepository<BecomeManagerRequest, Long> {

    List<BecomeManagerRequest> findByUserAndStatus(User user, RequestStatus pending);
}
