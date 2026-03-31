package com.billiard.tables;

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

public interface BilliardTableRepository
        extends JpaRepository<BilliardTable, Long>, JpaSpecificationExecutor<BilliardTable> {

    Optional<BilliardTable> findByNameIgnoreCase(String name);

    List<BilliardTable> findAllByStatus(TableStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM BilliardTable t WHERE t.id = :id")
    Optional<BilliardTable> findByIdForUpdate(Long id);

    @Override
    @EntityGraph(attributePaths = {"tableType"})
    Page<BilliardTable> findAll(Specification<BilliardTable> spec, Pageable pageable);
}
