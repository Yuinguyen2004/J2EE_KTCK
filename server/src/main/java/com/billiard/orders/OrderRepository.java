package com.billiard.orders;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository
        extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    List<Order> findAllBySession_IdOrderByOrderedAtDesc(Long sessionId);

    @Query("""
            select orderEntry
            from Order orderEntry
            where orderEntry.session.id = :sessionId
              and (orderEntry.status = com.billiard.orders.OrderStatus.CONFIRMED
                   or orderEntry.status is null)
            order by orderEntry.orderedAt desc
            """)
    List<Order> findBillableBySessionId(@Param("sessionId") Long sessionId);

    @Override
    @EntityGraph(attributePaths = {"session", "session.table", "customer", "customer.user", "staff"})
    Optional<Order> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select orderEntry
            from Order orderEntry
            left join fetch orderEntry.session session
            left join fetch session.table
            left join fetch orderEntry.customer customer
            left join fetch customer.user
            left join fetch orderEntry.staff
            where orderEntry.id = :id
            """)
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"session", "session.table", "customer", "customer.user", "staff"})
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);
}
