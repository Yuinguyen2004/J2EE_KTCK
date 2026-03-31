package com.billiard.tables;

import com.billiard.shared.web.PageRequestFactory;
import com.billiard.shared.web.PageResponse;
import com.billiard.tables.dto.PricingRuleResponse;
import com.billiard.tables.dto.PricingRuleUpsertRequest;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PricingRuleCrudService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "tableTypeName", "tableType.name",
            "blockMinutes", "blockMinutes",
            "pricePerMinute", "pricePerMinute",
            "sortOrder", "sortOrder",
            "active", "active",
            "updatedAt", "updatedAt"
    );

    private final PricingRuleRepository pricingRuleRepository;
    private final TableTypeRepository tableTypeRepository;

    public PricingRuleCrudService(
            PricingRuleRepository pricingRuleRepository,
            TableTypeRepository tableTypeRepository
    ) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.tableTypeRepository = tableTypeRepository;
    }

    @Transactional
    public PricingRuleResponse create(PricingRuleUpsertRequest request) {
        PricingRule pricingRule = new PricingRule();
        applyRequest(pricingRule, request);
        return toResponse(pricingRuleRepository.save(pricingRule));
    }

    @Transactional
    public PricingRuleResponse update(Long id, PricingRuleUpsertRequest request) {
        PricingRule pricingRule = findEntity(id);
        applyRequest(pricingRule, request);
        return toResponse(pricingRuleRepository.save(pricingRule));
    }

    @Transactional
    public PricingRuleResponse updateActive(Long id, boolean active) {
        PricingRule pricingRule = findEntity(id);
        pricingRule.setActive(active);
        return toResponse(pricingRuleRepository.save(pricingRule));
    }

    @Transactional(readOnly = true)
    public PricingRuleResponse get(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<PricingRuleResponse> list(
            String q,
            Integer page,
            Integer size,
            String sortBy,
            String direction
    ) {
        Pageable pageable = PageRequestFactory.create(
                page,
                size,
                sortBy,
                direction,
                "createdAt",
                SORT_FIELDS
        );
        Page<PricingRule> pricingRules = pricingRuleRepository.findAll(buildSpecification(q), pageable);
        return PageResponse.from(pricingRules, this::toResponse);
    }

    private void applyRequest(PricingRule pricingRule, PricingRuleUpsertRequest request) {
        pricingRule.setTableType(findTableType(request.tableTypeId()));
        pricingRule.setBlockMinutes(request.blockMinutes());
        pricingRule.setPricePerMinute(request.pricePerMinute());
        pricingRule.setSortOrder(request.sortOrder());
        pricingRule.setActive(
                request.active() == null ? pricingRule.isActive() : request.active()
        );
    }

    private PricingRuleResponse toResponse(PricingRule pricingRule) {
        TableType tableType = pricingRule.getTableType();
        return new PricingRuleResponse(
                pricingRule.getId(),
                tableType.getId(),
                tableType.getName(),
                pricingRule.getBlockMinutes(),
                pricingRule.getPricePerMinute(),
                pricingRule.getSortOrder(),
                pricingRule.isActive(),
                pricingRule.getCreatedAt(),
                pricingRule.getUpdatedAt()
        );
    }

    private PricingRule findEntity(Long id) {
        return pricingRuleRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Pricing rule not found"
        ));
    }

    private TableType findTableType(Long id) {
        return tableTypeRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table type not found"
        ));
    }

    private Specification<PricingRule> buildSpecification(String q) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(q)) {
                return criteriaBuilder.conjunction();
            }

            query.distinct(true);
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            var tableTypeJoin = root.join("tableType", JoinType.INNER);
            var predicates = new ArrayList<Predicate>();
            predicates.add(
                    criteriaBuilder.like(criteriaBuilder.lower(tableTypeJoin.get("name")), pattern, '\\')
            );
            return criteriaBuilder.or(predicates.toArray(Predicate[]::new));
        };
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
