package com.billiard.auth.dto;

import com.billiard.users.UserRole;

public record AuthUserResponse(
        String id,
        String email,
        String fullName,
        UserRole role
) {
}
