package com.mas.masServer.customException;

public class BiometricEnrollmentException extends RuntimeException {
    public BiometricEnrollmentException(String message) {
        super(message);
    }
}