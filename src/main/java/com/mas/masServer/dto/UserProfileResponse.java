// UserProfileResponse (basic info)
package com.mas.masServer.dto;

import lombok.Data;

import java.sql.Blob;
import java.time.LocalDate;

@Data
public class UserProfileResponse {
    private Long userId;
    private String firstName;
    private String middleName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String emailId;
    private String contactNumber;
    private Blob image;
    private String roleName;  // ADMIN/USER
}