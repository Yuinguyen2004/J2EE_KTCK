package com.billiard.auth;

import com.billiard.auth.dto.AuthUserResponse;

record AuthenticatedSession(
        String accessToken,
        String refreshToken,
        AuthUserResponse user
) {
}
