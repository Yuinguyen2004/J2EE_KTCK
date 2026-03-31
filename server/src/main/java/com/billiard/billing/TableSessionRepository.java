package com.billiard.billing;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TableSessionRepository extends JpaRepository<TableSession, Long> {

    Optional<TableSession> findFirstByTable_IdAndStatusInOrderByStartedAtDesc(
            Long tableId,
            Collection<SessionStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TableSession s WHERE s.id = :id")
    Optional<TableSession> findByIdForUpdate(Long id);
}
