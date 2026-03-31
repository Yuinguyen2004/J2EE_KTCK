package com.billiard.users.dto;

import com.billiard.auth.AuthProvider;
import com.billiard.users.UserRole;
import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        UserRole role,
        AuthProvider provider,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
