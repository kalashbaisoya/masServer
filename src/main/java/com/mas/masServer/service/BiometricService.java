package com.mas.masServer.service;

import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mas.masServer.dto.BiometricRegistrationRequest;
import com.mas.masServer.dto.BiometricVerifyRequest;
import com.mas.masServer.dto.EncryptionResult;
import com.mas.masServer.entity.User;
import com.mas.masServer.entity.UserBiometric;
import com.mas.masServer.repository.UserBiometricRepository;
import com.mas.masServer.repository.UserRepository;
import com.mas.masServer.config.AesGcmUtil;
import com.mas.masServer.config.BiomEnrollVerifyClient;

@Service
public class BiometricService {

    private final UserRepository userRepository;
    private final UserBiometricRepository biometricRepository;
    private final BiomEnrollVerifyClient biomClient;
    private final byte[] aesKey;

    public BiometricService(
            UserRepository userRepository,
            UserBiometricRepository biometricRepository,
            BiomEnrollVerifyClient biomClient,
            @Value("${security.biometric.aes.key}") String base64Key
    ) {
        this.userRepository = userRepository;
        this.biometricRepository = biometricRepository;
        this.biomClient = biomClient;
        this.aesKey = Base64.getDecoder().decode(base64Key);
    }

    @Transactional
    public void registerBiometric(BiometricRegistrationRequest request) {

        User user = userRepository.findByEmailId(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String mergedTemplate = biomClient.enrollAndMerge(
                request.getFingerprints()
        );

        EncryptionResult encrypted = AesGcmUtil.encrypt(
                mergedTemplate,
                aesKey
        );

        UserBiometric biometric = new UserBiometric();
        biometric.setUser(user);
        biometric.setEncryptedTemplate(encrypted.getEncryptedData());
        biometric.setIv(encrypted.getIv());
        biometric.setEnrolledAt(LocalDateTime.now());

        biometricRepository.save(biometric);
    }

    public boolean verifyBiometric(BiometricVerifyRequest request) {

        UserBiometric biometric = biometricRepository
                .findByUser_EmailId(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Biometric not enrolled"));

        String decryptedTemplate = AesGcmUtil.decrypt(
                biometric.getEncryptedTemplate(),
                biometric.getIv(),
                aesKey
        );

        return biomClient.verify(
                decryptedTemplate,
                request.getTemplateToVerify()
        );
    }
}

