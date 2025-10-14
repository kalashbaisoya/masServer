package com.mas.masServer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.SecurityQuestion;

public interface SecurityQuestionRepository extends JpaRepository<SecurityQuestion,Long> {
    
}
