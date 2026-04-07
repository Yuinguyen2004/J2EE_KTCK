package com.billiard.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OAuthExchangeCodeStore {

    private static final int CODE_BYTE_LENGTH = 32;
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, StoredCode> codes = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final AuthProperties authProperties;
    private final Clock clock;

    public OAuthExchangeCodeStore(AuthProperties authProperties, Clock clock) {
        this.authProperties = authProperties;
        this.clock = clock;
    }

    public String issueCode(String email) {
        cleanupExpiredEntries();
        if (codes.size() >= MAX_ENTRIES) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Exchange code store is full");
        }

        String code = nextCode();
        Instant now = Instant.now(clock);
        codes.put(
                code,
                new StoredCode(
                        email,
                        now,
                        now.plus(authProperties.getOauthExchangeCodeTtl())
                )
        );
        return code;
    }

    public String consumeCode(String rawCode) {
        cleanupExpiredEntries();

        if (!StringUtils.hasText(rawCode)) {
            throw invalidCode();
        }

        StoredCode storedCode = codes.remove(rawCode.trim());
        if (storedCode == null || !storedCode.expiresAt().isAfter(Instant.now(clock))) {
            throw invalidCode();
        }

        return storedCode.email();
    }

    private void cleanupExpiredEntries() {
        Instant now = Instant.now(clock);
        codes.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private String nextCode() {
        String code;
        do {
            byte[] bytes = new byte[CODE_BYTE_LENGTH];
            secureRandom.nextBytes(bytes);
            code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (codes.containsKey(code));

        return code;
    }

    private ResponseStatusException invalidCode() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid or expired OAuth exchange code"
        );
    }

    private record StoredCode(
            String email,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
