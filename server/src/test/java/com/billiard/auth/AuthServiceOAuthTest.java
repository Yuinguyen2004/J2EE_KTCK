package com.billiard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import com.billiard.customers.Customer;
import com.billiard.customers.CustomerRepository;
import io.jsonwebtoken.Claims;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceOAuthTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

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
                customerRepository,
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
    void issueGoogleExchangeCodeCreatesCustomerAccountForNewUser() {
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);

        when(userRepository.findByEmailIgnoreCase("google-user@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(userCaptor.capture())).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(41L);
            return savedUser;
        });
        when(customerRepository.findByUser_Id(41L)).thenReturn(Optional.empty());
        when(customerRepository.save(customerCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(oauthExchangeCodeStore.issueCode("google-user@example.com")).thenReturn("exchange-code");

        String exchangeCode = authService.issueGoogleExchangeCode(
                "Google-User@example.com",
                "Google User"
        );

        User savedUser = userCaptor.getValue();
        assertThat(exchangeCode).isEqualTo("exchange-code");
        assertThat(savedUser.getEmail()).isEqualTo("google-user@example.com");
        assertThat(savedUser.getFullName()).isEqualTo("Google User");
        assertThat(savedUser.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(savedUser.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(savedUser.isActive()).isTrue();
        assertThat(customerCaptor.getValue().getUser()).isSameAs(savedUser);
    }

    @Test
    void issueGoogleExchangeCodeAllowsExistingOperationalLocalAccount() {
        User existingUser = new User();
        existingUser.setEmail("existing@example.com");
        existingUser.setProvider(AuthProvider.LOCAL);
        existingUser.setRole(UserRole.ADMIN);
        existingUser.setActive(true);

        when(userRepository.findByEmailIgnoreCase("existing@example.com"))
                .thenReturn(Optional.of(existingUser));
        when(oauthExchangeCodeStore.issueCode("existing@example.com")).thenReturn("exchange-code");

        String exchangeCode = authService.issueGoogleExchangeCode("existing@example.com", "Admin");

        assertThat(exchangeCode).isEqualTo("exchange-code");
    }

    @Test
    void issueGoogleExchangeCodeRejectsExistingLocalCustomerAccount() {
        User existingUser = new User();
        existingUser.setEmail("existing@example.com");
        existingUser.setProvider(AuthProvider.LOCAL);
        existingUser.setRole(UserRole.CUSTOMER);
        existingUser.setActive(true);

        when(userRepository.findByEmailIgnoreCase("existing@example.com"))
                .thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> authService.issueGoogleExchangeCode("existing@example.com", "Customer"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void exchangeOAuthCodeReturnsNormalAuthenticatedSession() {
        User googleUser = new User();
        googleUser.setId(14L);
        googleUser.setEmail("google-user@example.com");
        googleUser.setFullName("Google User");
        googleUser.setRole(UserRole.CUSTOMER);
        googleUser.setProvider(AuthProvider.GOOGLE);
        googleUser.setActive(true);

        when(oauthExchangeCodeStore.consumeCode("exchange-code"))
                .thenReturn("google-user@example.com");
        when(userRepository.findByEmailIgnoreCase("google-user@example.com"))
                .thenReturn(Optional.of(googleUser));
        when(jwtProvider.createAccessToken(googleUser)).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(googleUser)).thenReturn("refresh-token");

        Claims refreshClaims = mock(Claims.class);
        when(refreshClaims.getId()).thenReturn("test-jti");
        when(refreshClaims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 86400_000));
        when(jwtProvider.validateRefreshToken("refresh-token")).thenReturn(refreshClaims);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthenticatedSession session = authService.exchangeOAuthCode("exchange-code");

        assertThat(session.accessToken()).isEqualTo("access-token");
        assertThat(session.refreshToken()).isEqualTo("refresh-token");
        assertThat(session.user().email()).isEqualTo("google-user@example.com");
        assertThat(session.user().role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void exchangeOAuthCodeAllowsOperationalLocalAccount() {
        User adminUser = new User();
        adminUser.setId(14L);
        adminUser.setEmail("admin@example.com");
        adminUser.setFullName("Admin User");
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setProvider(AuthProvider.LOCAL);
        adminUser.setActive(true);

        when(oauthExchangeCodeStore.consumeCode("exchange-code"))
                .thenReturn("admin@example.com");
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(adminUser));
        when(jwtProvider.createAccessToken(adminUser)).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(adminUser)).thenReturn("refresh-token");

        Claims refreshClaims = mock(Claims.class);
        when(refreshClaims.getId()).thenReturn("test-jti");
        when(refreshClaims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 86400_000));
        when(jwtProvider.validateRefreshToken("refresh-token")).thenReturn(refreshClaims);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthenticatedSession session = authService.exchangeOAuthCode("exchange-code");

        assertThat(session.accessToken()).isEqualTo("access-token");
        assertThat(session.refreshToken()).isEqualTo("refresh-token");
        assertThat(session.user().email()).isEqualTo("admin@example.com");
        assertThat(session.user().role()).isEqualTo(UserRole.ADMIN);
    }
}
