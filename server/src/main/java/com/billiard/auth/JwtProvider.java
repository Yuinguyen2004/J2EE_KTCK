package com.billiard.auth;

import com.billiard.users.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ROLE_CLAIM = "role";
    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";

    private final JwtProperties jwtProperties;

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String createAccessToken(User user) {
        return createToken(user, ACCESS_TOKEN, jwtProperties.getAccessTokenTtl());
    }

    public String createRefreshToken(User user) {
        return createToken(user, REFRESH_TOKEN, jwtProperties.getRefreshTokenTtl());
    }

    public Claims validateAccessToken(String token) {
        return validate(token, ACCESS_TOKEN);
    }

    public Claims validateRefreshToken(String token) {
        return validate(token, REFRESH_TOKEN);
    }

    private String createToken(User user, String tokenType, java.time.Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .claim(ROLE_CLAIM, user.getRole().name())
                .signWith(signingKey())
                .compact();
    }

    private Claims validate(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("Unexpected token type");
        }

        return claims;
    }

    private volatile SecretKey cachedKey;

    private SecretKey signingKey() {
        SecretKey key = cachedKey;
        if (key == null) {
            byte[] secretBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
            if (secretBytes.length < 32) {
                throw new IllegalStateException("JWT secret must be at least 32 bytes long");
            }
            key = Keys.hmacShaKeyFor(secretBytes);
            cachedKey = key;
        }
        return key;
    }
}
