package com.billiard.auth;

import com.billiard.auth.dto.AuthResponse;
import com.billiard.auth.dto.AuthUserResponse;
import com.billiard.auth.dto.ForgotPasswordRequest;
import com.billiard.auth.dto.LoginRequest;
import com.billiard.auth.dto.MessageResponse;
import com.billiard.auth.dto.OAuthExchangeRequest;
import com.billiard.auth.dto.RegisterRequest;
import com.billiard.auth.dto.ResetPasswordRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    public AuthController(AuthService authService, AuthCookieService authCookieService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        requireXmlHttpRequest(httpRequest);
        return authenticatedResponse(authService.register(request), response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        requireXmlHttpRequest(httpRequest);
        return authenticatedResponse(authService.login(request), response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        requireXmlHttpRequest(request);
        String refreshToken = authCookieService.extractRefreshToken(request);
        return authenticatedResponse(authService.refresh(refreshToken), response);
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        requireXmlHttpRequest(request);
        String refreshToken = authCookieService.extractRefreshToken(request);
        authService.logout(refreshToken);
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                authCookieService.clearRefreshTokenCookie().toString()
        );
        return ResponseEntity.ok(new MessageResponse("Logged out"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        authService.forgotPassword(request);
        return ResponseEntity.accepted()
                .body(new MessageResponse("If the account exists, a reset email has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("Password has been reset"));
    }

    @PostMapping("/oauth2/exchange")
    public ResponseEntity<AuthResponse> exchangeOAuthCode(
            @Valid @RequestBody OAuthExchangeRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        requireXmlHttpRequest(httpRequest);
        return authenticatedResponse(authService.exchangeOAuthCode(request.code()), response);
    }

    @GetMapping("/me")
    public AuthUserResponse me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return authService.me(authentication.getName());
    }

    private ResponseEntity<AuthResponse> authenticatedResponse(
            AuthenticatedSession session,
            HttpServletResponse response
    ) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                authCookieService.refreshTokenCookie(session.refreshToken()).toString()
        );
        return ResponseEntity.ok(new AuthResponse(session.accessToken(), session.user()));
    }

    private void requireXmlHttpRequest(HttpServletRequest request) {
        if (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing required header");
        }
    }
}
