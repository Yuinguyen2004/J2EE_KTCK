package com.billiard.billing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionPauseRepository extends JpaRepository<SessionPause, Long> {

    List<SessionPause> findAllBySession_IdOrderByStartedAtAsc(Long sessionId);

    Optional<SessionPause> findFirstBySession_IdAndEndedAtIsNullOrderByStartedAtDesc(Long sessionId);
}
