package com.billiard.tables;

import com.billiard.reservations.ReservationRepository;
import com.billiard.reservations.ReservationStatus;
import com.billiard.shared.web.PageRequestFactory;
import com.billiard.shared.web.PageResponse;
import com.billiard.tables.dto.CustomerTableAvailabilityResponse;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CustomerTableService {

    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES = List.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN
    );

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "name",
            "tableTypeName", "tableType.name",
            "status", "status",
            "updatedAt", "updatedAt"
    );

    private final BilliardTableRepository billiardTableRepository;
    private final ReservationRepository reservationRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final Clock clock;

    public CustomerTableService(
            BilliardTableRepository billiardTableRepository,
            ReservationRepository reservationRepository,
            PricingRuleRepository pricingRuleRepository,
            Clock clock
    ) {
        this.billiardTableRepository = billiardTableRepository;
        this.reservationRepository = reservationRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerTableAvailabilityResponse> list(
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
                "name",
                SORT_FIELDS
        );
        Page<BilliardTable> tables = billiardTableRepository.findAll(buildSpecification(q), pageable);
        Set<Long> reservedTableIds = resolveReservedTableIds(tables.getContent());
        Map<Long, BigDecimal> hourlyRateByTypeId = resolveHourlyRates(tables.getContent());
        return new PageResponse<>(
                tables.getContent().stream()
                        .map(table -> toResponse(
                                table,
                                reservedTableIds.contains(table.getId()),
                                hourlyRateByTypeId.get(table.getTableType().getId())
                        ))
                        .toList(),
                tables.getNumber(),
                tables.getSize(),
                tables.getTotalElements(),
                tables.getTotalPages()
        );
    }

    private Specification<BilliardTable> buildSpecification(String q) {
        return (root, query, criteriaBuilder) -> {
            if (query != null) {
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isTrue(root.get("active")));
            predicates.add(criteriaBuilder.isTrue(root.join("tableType", JoinType.INNER).get("active")));

            if (StringUtils.hasText(q)) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                var tableTypeJoin = root.join("tableType", JoinType.INNER);
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(tableTypeJoin.get("name")), pattern, '\\'),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("status").as(String.class)),
                                pattern,
                                '\\'
                        )
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Set<Long> resolveReservedTableIds(Collection<BilliardTable> tables) {
        if (tables.isEmpty()) {
            return Set.of();
        }

        Instant now = Instant.now(clock);
        return new HashSet<>(reservationRepository.findDistinctTableIdsWithActiveReservations(
                tables.stream().map(BilliardTable::getId).toList(),
                ACTIVE_RESERVATION_STATUSES,
                now,
                now
        ));
    }

    private Map<Long, BigDecimal> resolveHourlyRates(Collection<BilliardTable> tables) {
        Map<Long, BigDecimal> rates = new HashMap<>();
        tables.stream()
                .map(BilliardTable::getTableType)
                .map(TableType::getId)
                .distinct()
                .forEach(tableTypeId -> pricingRuleRepository
                        .findAllByTableType_IdAndActiveTrueOrderBySortOrderAsc(tableTypeId)
                        .stream()
                        .findFirst()
                        .ifPresent(rule -> rates.put(
                                tableTypeId,
                                rule.getPricePerMinute().multiply(BigDecimal.valueOf(60L))
                        )));
        return rates;
    }

    private CustomerTableAvailabilityResponse toResponse(
            BilliardTable table,
            boolean hasActiveReservation,
            BigDecimal pricePerHour
    ) {
        TableType tableType = table.getTableType();
        return new CustomerTableAvailabilityResponse(
                table.getId(),
                table.getName(),
                tableType.getId(),
                tableType.getName(),
                resolveStatus(table.getStatus(), hasActiveReservation),
                pricePerHour
        );
    }

    private TableStatus resolveStatus(TableStatus storedStatus, boolean hasActiveReservation) {
        if (storedStatus == TableStatus.IN_USE
                || storedStatus == TableStatus.PAUSED
                || storedStatus == TableStatus.MAINTENANCE) {
            return storedStatus;
        }

        if (hasActiveReservation) {
            return TableStatus.RESERVED;
        }

        return storedStatus == TableStatus.RESERVED ? TableStatus.AVAILABLE : storedStatus;
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
