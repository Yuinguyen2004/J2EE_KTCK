package com.billiard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.billiard.auth.dto.ResetPasswordRequest;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTokenSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private AuthMailService authMailService;

    @Mock
    private OAuthExchangeCodeStore oauthExchangeCodeStore;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordResetTokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtProvider,
                authMailService,
                new AuthProperties(),
                oauthExchangeCodeStore
        );
    }

    @Test
    void resetPasswordRevokesActiveRefreshTokens() {
        User user = new User();
        user.setId(14L);
        user.setEmail("customer@example.com");
        user.setProvider(AuthProvider.LOCAL);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setId(9L);
        resetToken.setUser(user);
        resetToken.setExpiresAt(Instant.now().plusSeconds(300));

        when(passwordResetTokenRepository.findByTokenHashAndUsedAtIsNull(anyString()))
                .thenReturn(Optional.of(resetToken));
        when(passwordResetTokenRepository.findAllByUser_IdAndUsedAtIsNull(14L))
                .thenReturn(List.of(resetToken));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("encoded-password");

        authService.resetPassword(new ResetPasswordRequest("raw-reset-token", "NewPassword1"));

        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        verify(refreshTokenRepository).revokeActiveByUserId(eq(14L), any(Instant.class));
        verify(userRepository).save(user);
    }

    @Test
    void refreshLocksStoredTokenBeforeRotation() {
        User user = new User();
        user.setId(7L);
        user.setEmail("customer@example.com");
        user.setFullName("Customer");
        user.setRole(UserRole.CUSTOMER);
        user.setProvider(AuthProvider.LOCAL);
        user.setActive(true);

        RefreshToken storedToken = new RefreshToken();
        storedToken.setTokenFamily("family-1");
        storedToken.setExpiresAt(Instant.now().plusSeconds(300));

        Claims incomingClaims = org.mockito.Mockito.mock(Claims.class);
        when(incomingClaims.getId()).thenReturn("incoming-jti");
        when(incomingClaims.getSubject()).thenReturn("customer@example.com");

        Claims rotatedClaims = org.mockito.Mockito.mock(Claims.class);
        when(rotatedClaims.getId()).thenReturn("rotated-jti");
        when(rotatedClaims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(600)));

        when(jwtProvider.validateRefreshToken("incoming-refresh")).thenReturn(incomingClaims);
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(storedToken));
        when(userRepository.findByEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.of(user));
        when(jwtProvider.createAccessToken(user)).thenReturn("new-access-token");
        when(jwtProvider.createRefreshToken(user)).thenReturn("new-refresh-token");
        when(jwtProvider.validateRefreshToken("new-refresh-token")).thenReturn(rotatedClaims);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthenticatedSession session = authService.refresh("incoming-refresh");

        assertThat(session.accessToken()).isEqualTo("new-access-token");
        assertThat(session.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(storedToken.getRotatedAt()).isNotNull();
        verify(refreshTokenRepository).findByTokenHashForUpdate(anyString());
    }
}
