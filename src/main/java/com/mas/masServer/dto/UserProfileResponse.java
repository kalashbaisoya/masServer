// UserProfileResponse (basic info)
package com.mas.masServer.dto;

import lombok.Data;

// import java.sql.Blob;
import java.time.LocalDate;

import com.mas.masServer.entity.SystemRole;

@Data
public class UserProfileResponse {
    private Long userId;
    private String firstName;
    private String middleName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String emailId;
    private String contactNumber;
    private String image;
    private SystemRole systemRole;  // ADMIN/USER
}