package com.billiard.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

@Component
public class AuthCookieService {

    private final AuthProperties authProperties;
    private final JwtProperties jwtProperties;

    public AuthCookieService(AuthProperties authProperties, JwtProperties jwtProperties) {
        this.authProperties = authProperties;
        this.jwtProperties = jwtProperties;
    }

    public ResponseCookie refreshTokenCookie(String token) {
        return ResponseCookie.from(authProperties.getRefreshCookieName(), token)
                .httpOnly(true)
                .secure(authProperties.isRefreshCookieSecure())
                .sameSite("Lax")
                .path(authProperties.getRefreshCookiePath())
                .maxAge(jwtProperties.getRefreshTokenTtl())
                .build();
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return ResponseCookie.from(authProperties.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(authProperties.isRefreshCookieSecure())
                .sameSite("Lax")
                .path(authProperties.getRefreshCookiePath())
                .maxAge(Duration.ZERO)
                .build();
    }

    public String extractRefreshToken(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, authProperties.getRefreshCookieName());
        return cookie == null ? null : cookie.getValue();
    }
}
