// UserUpdateRequest
package com.mas.masServer.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UserUpdateRequest {
    private String firstName;
    private String middleName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String contactNumber;
    private String image;
}

