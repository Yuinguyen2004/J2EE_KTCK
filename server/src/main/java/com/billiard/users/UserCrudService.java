package com.billiard.users;

import com.billiard.auth.AuthProvider;
import com.billiard.shared.web.PageRequestFactory;
import com.billiard.shared.web.PageResponse;
import com.billiard.users.dto.UserResponse;
import com.billiard.users.dto.UserUpsertRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserCrudService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "email", "email",
            "fullName", "fullName",
            "role", "role",
            "active", "active",
            "updatedAt", "updatedAt"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserCrudService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(UserUpsertRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setProvider(AuthProvider.LOCAL);
        applyRequest(user, request, true);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserUpsertRequest request) {
        User user = findEntity(id);
        String normalizedEmail = normalizeEmail(request.email());
        userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Email already exists"
                    );
                });

        user.setEmail(normalizedEmail);
        applyRequest(user, request, false);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateActive(Long id, boolean active) {
        User user = findEntity(id);
        user.setActive(active);
        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(
            String q,
            Integer page,
            Integer size,
            String sortBy,
            String direction
    ) {
        Pageable pageable = PageRequestFactory.create(
                page,
                size,
                sortBy,
                direction,
                "createdAt",
                SORT_FIELDS
        );
        Page<User> users = userRepository.findAll(buildSpecification(q), pageable);
        return PageResponse.from(users, this::toResponse);
    }

    private void applyRequest(User user, UserUpsertRequest request, boolean creating) {
        user.setFullName(request.fullName().trim());
        user.setPhone(normalizeNullable(request.phone()));
        user.setRole(request.role());
        user.setActive(request.active() == null ? user.isActive() : request.active());

        String password = normalizeNullable(request.password());
        if (creating && password == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "password is required for local users"
            );
        }

        if (password != null) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getProvider(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private User findEntity(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User not found"
        ));
    }

    private Specification<User> buildSpecification(String q) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(q)) {
                return criteriaBuilder.conjunction();
            }

            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            var predicates = new ArrayList<Predicate>();
            predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern, '\\'));
            predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), pattern, '\\'));
            predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("phone")), pattern, '\\'));

            return criteriaBuilder.or(predicates.toArray(Predicate[]::new));
        };
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
