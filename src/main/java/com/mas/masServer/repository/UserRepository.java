package com.mas.masServer.repository;

import com.mas.masServer.entity.User;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmailId(String emailId);
    boolean existsByContactNumber(String contactNumber);
    Optional<User> findByEmailId(String emailId);
    Optional<User> findByRoleRoleName(String string);
}
