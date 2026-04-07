package com.billiard.auth;

import com.billiard.auth.dto.AuthUserResponse;
import com.billiard.auth.dto.ForgotPasswordRequest;
import com.billiard.auth.dto.LoginRequest;
import com.billiard.auth.dto.RegisterRequest;
import com.billiard.auth.dto.ResetPasswordRequest;
import com.billiard.customers.Customer;
import com.billiard.customers.CustomerRepository;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$dummyHashToPreventTimingSideChannel000000000000000000";

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuthMailService authMailService;
    private final AuthProperties authProperties;
    private final OAuthExchangeCodeStore oauthExchangeCodeStore;

    public AuthService(
            UserRepository userRepository,
            CustomerRepository customerRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            AuthMailService authMailService,
            AuthProperties authProperties,
            OAuthExchangeCodeStore oauthExchangeCodeStore
    ) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.authMailService = authMailService;
        this.authProperties = authProperties;
        this.oauthExchangeCodeStore = oauthExchangeCodeStore;
    }

    @Transactional
    public AuthenticatedSession register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration could not be completed");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setRole(UserRole.CUSTOMER);
        user.setProvider(AuthProvider.LOCAL);
        user.setActive(true);

        User savedUser = userRepository.save(user);
        ensureCustomerProfile(savedUser);
        return authenticatedSession(savedUser);
    }

    @Transactional
    public AuthenticatedSession login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is disabled");
        }

        if (user.getProvider() != AuthProvider.LOCAL || user.getPasswordHash() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Use the original sign-in method for this account"
            );
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        return authenticatedSession(user);
    }

    @Transactional
    public AuthenticatedSession refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh token");
        }

        Instant now = Instant.now();
        Claims claims = validateRefreshToken(refreshToken);
        String jti = claims.getId();
        if (jti == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        String tokenHash = hashToken(jti);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (storedToken.getRotatedAt() != null || storedToken.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(storedToken.getTokenFamily(), now);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token reuse detected");
        }

        if (!storedToken.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        storedToken.setRotatedAt(now);
        refreshTokenRepository.save(storedToken);

        User user = loadActiveUser(claims.getSubject());
        return authenticatedSessionWithRotation(user, storedToken.getTokenFamily());
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            Claims claims = validateRefreshToken(refreshToken);
            String jti = claims.getId();
            if (jti != null) {
                refreshTokenRepository.findByTokenHash(hashToken(jti))
                        .ifPresent(token -> {
                            refreshTokenRepository.revokeFamily(token.getTokenFamily(), Instant.now());
                        });
            }
        } catch (ResponseStatusException ignored) {
            // Token already invalid, nothing to revoke
        }
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(user -> user.getProvider() == AuthProvider.LOCAL)
                .ifPresent(this::issuePasswordResetToken);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNull(hashToken(request.token()))
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid or expired reset token"
                ));

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setProvider(AuthProvider.LOCAL);
        userRepository.save(user);
        refreshTokenRepository.revokeActiveByUserId(user.getId(), Instant.now());

        markTokenUsed(resetToken);
        invalidateActiveResetTokens(user.getId(), resetToken);
    }

    @Transactional
    public String issueGoogleExchangeCode(String email, String fullName) {
        User user = resolveGoogleUser(email, fullName);
        return oauthExchangeCodeStore.issueCode(user.getEmail());
    }

    @Transactional
    public AuthenticatedSession exchangeOAuthCode(String code) {
        String email = oauthExchangeCodeStore.consumeCode(code);
        User user = loadActiveUser(email);

        if (user.getRole() == UserRole.CUSTOMER && user.getProvider() != AuthProvider.GOOGLE) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Use the original sign-in method for this account"
            );
        }

        return authenticatedSession(user);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me(String email) {
        return toAuthUser(loadActiveUser(email));
    }

    public String bearerSubject(String accessToken) {
        return validateAccessToken(accessToken).getSubject();
    }

    private AuthenticatedSession authenticatedSession(User user) {
        String tokenFamily = UUID.randomUUID().toString();
        return authenticatedSessionWithRotation(user, tokenFamily);
    }

    private AuthenticatedSession authenticatedSessionWithRotation(User user, String tokenFamily) {
        String accessToken = jwtProvider.createAccessToken(user);
        String refreshJwt = jwtProvider.createRefreshToken(user);

        Claims refreshClaims = jwtProvider.validateRefreshToken(refreshJwt);
        String jti = refreshClaims.getId();

        RefreshToken newToken = new RefreshToken();
        newToken.setUser(user);
        newToken.setTokenHash(hashToken(jti));
        newToken.setTokenFamily(tokenFamily);
        newToken.setExpiresAt(refreshClaims.getExpiration().toInstant());
        refreshTokenRepository.save(newToken);

        return new AuthenticatedSession(accessToken, refreshJwt, toAuthUser(user));
    }

    private void issuePasswordResetToken(User user) {
        invalidateActiveResetTokens(user.getId(), null);

        String rawToken = generateRawToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(hashToken(rawToken));
        resetToken.setExpiresAt(Instant.now().plus(authProperties.getPasswordResetTokenTtl()));
        passwordResetTokenRepository.save(resetToken);

        try {
            authMailService.sendPasswordReset(user, rawToken);
        } catch (MailException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to send password reset email",
                    ex
            );
        }
    }

    private void invalidateActiveResetTokens(Long userId, PasswordResetToken exclude) {
        List<PasswordResetToken> activeTokens =
                passwordResetTokenRepository.findAllByUser_IdAndUsedAtIsNull(userId);
        for (PasswordResetToken token : activeTokens) {
            if (exclude != null && token.getId().equals(exclude.getId())) {
                continue;
            }
            token.setUsedAt(Instant.now());
        }
        passwordResetTokenRepository.saveAll(activeTokens);
    }

    private void markTokenUsed(PasswordResetToken resetToken) {
        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);
    }

    private User resolveGoogleUser(String email, String fullName) {
        String normalizedEmail = normalizeEmail(email);

        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .map(existingUser -> reuseGoogleUser(existingUser))
                .orElseGet(() -> createGoogleUser(normalizedEmail, fullName));
    }

    private User reuseGoogleUser(User existingUser) {
        if (!existingUser.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is disabled");
        }

        if (existingUser.getRole() != UserRole.CUSTOMER) {
            return existingUser;
        }

        if (existingUser.getProvider() != AuthProvider.GOOGLE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This email is already linked to a different sign-in method"
            );
        }

        return existingUser;
    }

    private User createGoogleUser(String normalizedEmail, String fullName) {
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setFullName(normalizeOauthFullName(fullName, normalizedEmail));
        user.setRole(UserRole.CUSTOMER);
        user.setProvider(AuthProvider.GOOGLE);
        user.setActive(true);
        User savedUser = userRepository.save(user);
        ensureCustomerProfile(savedUser);
        return savedUser;
    }

    private User loadActiveUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is disabled");
        }

        return user;
    }

    private void ensureCustomerProfile(User user) {
        if (user.getRole() != UserRole.CUSTOMER || user.getId() == null) {
            return;
        }

        customerRepository.findByUser_Id(user.getId()).ifPresentOrElse(
                existing -> {
                },
                () -> {
                    Customer customer = new Customer();
                    customer.setUser(user);
                    customer.setMemberSince(Instant.now());
                    customerRepository.save(customer);
                }
        );
    }

    private AuthUserResponse toAuthUser(User user) {
        return new AuthUserResponse(
                user.getId() == null ? null : user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );
    }

    private Claims validateAccessToken(String token) {
        try {
            return jwtProvider.validateAccessToken(token);
        } catch (JwtException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token", ex);
        }
    }

    private Claims validateRefreshToken(String token) {
        try {
            return jwtProvider.validateRefreshToken(token);
        } catch (JwtException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token", ex);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOauthFullName(String fullName, String email) {
        int delimiterIndex = email.indexOf('@');
        String candidate = StringUtils.hasText(fullName)
                ? fullName.trim()
                : delimiterIndex > 0 ? email.substring(0, delimiterIndex) : email;

        return candidate.length() > 150
                ? candidate.substring(0, 150)
                : candidate;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for token hashing", ex);
        }
    }
}
