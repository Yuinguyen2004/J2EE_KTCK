package com.billiard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private BootstrapAdminProperties properties;
    private BootstrapAdminInitializer initializer;

    @BeforeEach
    void setUp() {
        properties = new BootstrapAdminProperties();
        initializer = new BootstrapAdminInitializer(properties, userRepository, passwordEncoder);
    }

    @Test
    void runSkipsWhenBootstrapCredentialsAreNotConfigured() throws Exception {
        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository, never()).existsByRole(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void runCreatesAdminWhenNoAdminExistsYet() throws Exception {
        properties.setEmail("Admin@Bida.local");
        properties.setPassword("ChangeMe123!");
        properties.setFullName("System Admin");
        properties.setPhone("0123456789");

        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("admin@bida.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("ChangeMe123!")).thenReturn("encoded-password");

        initializer.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("admin@bida.local");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getFullName()).isEqualTo("System Admin");
        assertThat(savedUser.getPhone()).isEqualTo("0123456789");
        assertThat(savedUser.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(savedUser.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(savedUser.isActive()).isTrue();
    }

    @Test
    void runFailsWhenOnlyOneBootstrapCredentialIsProvided() {
        properties.setEmail("admin@bida.local");

        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Both app.bootstrap.admin.email and app.bootstrap.admin.password are required");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void runSkipsWhenAdminAlreadyExists() throws Exception {
        properties.setEmail("admin@bida.local");
        properties.setPassword("ChangeMe123!");
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(true);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void runFailsWhenBootstrapEmailAlreadyBelongsToAnotherUser() {
        properties.setEmail("admin@bida.local");
        properties.setPassword("ChangeMe123!");

        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("admin@bida.local")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot bootstrap admin because email 'admin@bida.local' is already assigned");

        verify(userRepository, never()).save(any(User.class));
    }
}
