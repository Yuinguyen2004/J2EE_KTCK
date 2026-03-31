package com.billiard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OAuthExchangeCodeStoreTest {

    private MutableClock clock;
    private OAuthExchangeCodeStore oauthExchangeCodeStore;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-03-28T06:00:00Z"), ZoneId.of("UTC"));

        AuthProperties authProperties = new AuthProperties();
        authProperties.setOauthExchangeCodeTtl(Duration.ofSeconds(60));

        oauthExchangeCodeStore = new OAuthExchangeCodeStore(authProperties, clock);
    }

    @Test
    void issuedCodeCanBeConsumedOnce() {
        String code = oauthExchangeCodeStore.issueCode("google-user@example.com");

        assertThat(oauthExchangeCodeStore.consumeCode(code)).isEqualTo("google-user@example.com");
        assertThatThrownBy(() -> oauthExchangeCodeStore.consumeCode(code))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid or expired OAuth exchange code");
    }

    @Test
    void expiredCodeIsRejected() {
        String code = oauthExchangeCodeStore.issueCode("google-user@example.com");
        clock.advance(Duration.ofSeconds(61));

        assertThatThrownBy(() -> oauthExchangeCodeStore.consumeCode(code))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid or expired OAuth exchange code");
    }

    private static final class MutableClock extends Clock {

        private Instant currentInstant;
        private final ZoneId zoneId;

        private MutableClock(Instant currentInstant, ZoneId zoneId) {
            this.currentInstant = currentInstant;
            this.zoneId = zoneId;
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(currentInstant, zone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }

        private void advance(Duration duration) {
            currentInstant = currentInstant.plus(duration);
        }
    }
}
