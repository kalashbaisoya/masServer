package com.mas.masServer.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class UserRegisterRequest {
    private String firstName;
    private String middleName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String emailId;
    private String contactNumber;
    private String password;
    private List<SecurityAnswerRequest> securityAnswerRequest;
}
