package com.mas.masServer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.BecomeManagerRequest;

public interface BecomeManagerRequestRepository extends JpaRepository<BecomeManagerRequest, Long> {
}
