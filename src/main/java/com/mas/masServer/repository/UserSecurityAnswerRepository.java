package com.mas.masServer.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.User;
import com.mas.masServer.entity.UserSecurityAnswer;

public interface UserSecurityAnswerRepository extends JpaRepository<UserSecurityAnswer, Long> {

    Optional<UserSecurityAnswer> findByUserAndQuestion_questionId(User user, Long questionId);

    boolean existsByUserUserIdAndQuestionQuestionId(Long userId, Long questionId);

    boolean existsByUserUserId(Long userId);

    void deleteByUserUserId(Long userId);
    
}
