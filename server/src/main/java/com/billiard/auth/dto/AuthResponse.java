package com.billiard.auth.dto;

public record AuthResponse(
        String accessToken,
        AuthUserResponse user
) {
}
