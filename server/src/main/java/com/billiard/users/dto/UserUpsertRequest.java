package com.billiard.users.dto;

import com.billiard.users.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserUpsertRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,
        @NotBlank
        @Size(max = 150)
        String fullName,
        @Size(max = 20)
        String phone,
        @NotNull
        UserRole role,
        @Size(min = 8, max = 255)
        String password,
        Boolean active
) {
}
