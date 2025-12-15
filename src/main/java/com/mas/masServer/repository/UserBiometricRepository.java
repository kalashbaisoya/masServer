package com.mas.masServer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.UserBiometric;

import java.util.Optional;

public interface UserBiometricRepository extends JpaRepository<UserBiometric, Long> {
    Optional<UserBiometric> findByUser_UserId(Long userId);
    Optional<UserBiometric> findByUser_EmailId(String emailId);
}

