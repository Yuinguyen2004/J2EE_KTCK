package com.billiard.tables;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PricingRuleRepository
        extends JpaRepository<PricingRule, Long>, JpaSpecificationExecutor<PricingRule> {

    List<PricingRule> findAllByTableType_IdAndActiveTrueOrderBySortOrderAsc(Long tableTypeId);

    @Override
    @EntityGraph(attributePaths = {"tableType"})
    Page<PricingRule> findAll(Specification<PricingRule> spec, Pageable pageable);
}
