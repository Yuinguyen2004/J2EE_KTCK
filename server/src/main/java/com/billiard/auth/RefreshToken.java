package com.billiard.auth;

import com.billiard.shared.entity.AuditableEntity;
import com.billiard.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_token_hash", columnList = "tokenHash", unique = true),
    @Index(name = "idx_refresh_token_family", columnList = "tokenFamily")
})
public class RefreshToken extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(nullable = false, length = 64)
    private String tokenFamily;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    private Instant rotatedAt;

    public boolean isActive() {
        return revokedAt == null && rotatedAt == null && expiresAt.isAfter(Instant.now());
    }
}
