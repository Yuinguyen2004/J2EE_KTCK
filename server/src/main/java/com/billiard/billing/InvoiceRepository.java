package com.billiard.billing;

import com.billiard.reports.dto.RevenueBucketRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository
        extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findBySession_Id(Long sessionId);

    @Query("""
            SELECT new com.billiard.reports.dto.RevenueBucketRow(
                CAST(i.paidAt AS LocalDate),
                COUNT(i),
                COALESCE(SUM(i.totalAmount), 0)
            )
            FROM Invoice i
            WHERE i.status = com.billiard.billing.InvoiceStatus.PAID
              AND i.paidAt IS NOT NULL
              AND i.paidAt >= :fromInclusive
              AND i.paidAt < :toExclusive
            GROUP BY CAST(i.paidAt AS LocalDate)
            ORDER BY CAST(i.paidAt AS LocalDate)
            """)
    List<RevenueBucketRow> aggregateDailyRevenue(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );

    @Override
    @EntityGraph(attributePaths = {"session", "session.table", "customer", "customer.user", "issuedBy"})
    Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);
}
