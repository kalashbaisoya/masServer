package com.mas.masServer.repository;

import com.mas.masServer.entity.OTP;
import com.mas.masServer.entity.User;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OTPRepository extends JpaRepository<OTP,Long> {

    Optional<OTP> findByUserAndOtpCodeAndIsUsedFalse(User user, String otpCode);
    
}
