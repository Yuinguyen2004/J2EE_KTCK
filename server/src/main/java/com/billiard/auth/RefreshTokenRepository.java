package com.billiard.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.tokenFamily = :family AND r.revokedAt IS NULL AND r.rotatedAt IS NULL")
    void revokeFamily(String family, Instant revokedAt);

    @Modifying
    @Query("""
            UPDATE RefreshToken r
            SET r.revokedAt = :revokedAt
            WHERE r.user.id = :userId
              AND r.revokedAt IS NULL
              AND r.rotatedAt IS NULL
            """)
    void revokeActiveByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < CURRENT_TIMESTAMP")
    void deleteExpired();
}
