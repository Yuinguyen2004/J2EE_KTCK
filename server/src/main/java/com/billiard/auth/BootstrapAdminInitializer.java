package com.billiard.auth;

import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
            BootstrapAdminProperties properties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = normalizeEmail(properties.getEmail());
        String password = normalizeNullable(properties.getPassword());

        if (email == null && password == null) {
            return;
        }

        if (email == null || password == null) {
            throw new IllegalStateException(
                    "Both app.bootstrap.admin.email and app.bootstrap.admin.password are required to bootstrap the first admin"
            );
        }

        if (userRepository.existsByRole(UserRole.ADMIN)) {
            LOGGER.info("Skipping bootstrap admin creation because an admin user already exists");
            return;
        }

        userRepository.findByEmailIgnoreCase(email).ifPresent(existing -> {
            throw new IllegalStateException(
                    "Cannot bootstrap admin because email '" + email + "' is already assigned to another user"
            );
        });

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(normalizeNullable(properties.getFullName()) == null
                ? "System Admin"
                : properties.getFullName().trim());
        user.setPhone(normalizeNullable(properties.getPhone()));
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(UserRole.ADMIN);
        user.setActive(true);
        userRepository.save(user);

        LOGGER.warn("Bootstrapped initial admin user '{}'. Remove bootstrap admin env vars after first successful deploy.", email);
    }

    private String normalizeEmail(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
