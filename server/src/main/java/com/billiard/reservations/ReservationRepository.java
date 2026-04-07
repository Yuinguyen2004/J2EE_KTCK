package com.billiard.reservations;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    boolean existsByTable_IdAndStatusInAndReservedFromLessThanAndReservedToGreaterThan(
            Long tableId,
            Collection<ReservationStatus> statuses,
            Instant reservedTo,
            Instant reservedFrom
    );

    boolean existsByTable_IdAndIdNotAndStatusInAndReservedFromLessThanAndReservedToGreaterThan(
            Long tableId,
            Long reservationId,
            Collection<ReservationStatus> statuses,
            Instant reservedTo,
            Instant reservedFrom
    );

    @Query("""
            select distinct reservation.table.id
            from Reservation reservation
            where reservation.table.id in :tableIds
              and reservation.status in :statuses
              and reservation.reservedFrom < :reservedTo
              and reservation.reservedTo > :reservedFrom
            """)
    List<Long> findDistinctTableIdsWithActiveReservations(
            @Param("tableIds") Collection<Long> tableIds,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("reservedTo") Instant reservedTo,
            @Param("reservedFrom") Instant reservedFrom
    );

    @Override
    @EntityGraph(attributePaths = {"table", "customer", "customer.user", "staff"})
    Page<Reservation> findAll(Specification<Reservation> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"table", "customer", "customer.user", "staff"})
    java.util.Optional<Reservation> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reservation
            from Reservation reservation
            where reservation.id = :id
            """)
    java.util.Optional<Reservation> findByIdForUpdate(@Param("id") Long id);
}
