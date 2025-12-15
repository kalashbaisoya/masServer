package com.mas.masServer.config;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import com.mas.masServer.dto.EncryptionResult;

@Component
public class AesGcmUtil {

    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    public static EncryptionResult encrypt(String plainText, byte[] key) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKey secretKey = new SecretKeySpec(key, "AES");

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes());

            return new EncryptionResult(
                    Base64.getEncoder().encodeToString(encrypted),
                    Base64.getEncoder().encodeToString(iv)
            );

        } catch (Exception e) {
            throw new RuntimeException("Biometric encryption failed");
        }
    }

    public static String decrypt(String cipherText, String ivBase64, byte[] key) {
        try {
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] encrypted = Base64.getDecoder().decode(cipherText);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKey secretKey = new SecretKeySpec(key, "AES");

            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted));

        } catch (Exception e) {
            throw new RuntimeException("Biometric decryption failed");
        }
    }
}
