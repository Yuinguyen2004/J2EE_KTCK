package com.billiard.tables;

import com.billiard.reservations.ReservationRepository;
import com.billiard.reservations.ReservationStatus;
import com.billiard.shared.web.PageRequestFactory;
import com.billiard.shared.web.PageResponse;
import com.billiard.tables.dto.BilliardTableResponse;
import com.billiard.tables.dto.BilliardTableUpsertRequest;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BilliardTableCrudService {

    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES = List.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN
    );

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "name", "name",
            "tableTypeName", "tableType.name",
            "status", "status",
            "floorPositionX", "floorPositionX",
            "floorPositionY", "floorPositionY",
            "active", "active",
            "updatedAt", "updatedAt"
    );

    private final BilliardTableRepository billiardTableRepository;
    private final TableTypeRepository tableTypeRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public BilliardTableCrudService(
            BilliardTableRepository billiardTableRepository,
            TableTypeRepository tableTypeRepository,
            ReservationRepository reservationRepository,
            Clock clock
    ) {
        this.billiardTableRepository = billiardTableRepository;
        this.tableTypeRepository = tableTypeRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    @Transactional
    public BilliardTableResponse create(BilliardTableUpsertRequest request) {
        String normalizedName = normalizeName(request.name());
        billiardTableRepository.findByNameIgnoreCase(normalizedName).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Table name already exists");
        });

        BilliardTable billiardTable = new BilliardTable();
        billiardTable.setName(normalizedName);
        applyRequest(billiardTable, request);
        BilliardTable savedTable = billiardTableRepository.save(billiardTable);
        return toResponse(
                savedTable,
                resolveReservedTableIds(List.of(savedTable)).contains(savedTable.getId())
        );
    }

    @Transactional
    public BilliardTableResponse update(Long id, BilliardTableUpsertRequest request) {
        BilliardTable billiardTable = findEntity(id);
        String normalizedName = normalizeName(request.name());
        billiardTableRepository.findByNameIgnoreCase(normalizedName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Table name already exists"
                    );
                });

        billiardTable.setName(normalizedName);
        applyRequest(billiardTable, request);
        BilliardTable savedTable = billiardTableRepository.save(billiardTable);
        return toResponse(
                savedTable,
                resolveReservedTableIds(List.of(savedTable)).contains(savedTable.getId())
        );
    }

    @Transactional
    public BilliardTableResponse updateActive(Long id, boolean active) {
        BilliardTable billiardTable = findEntity(id);
        billiardTable.setActive(active);
        BilliardTable savedTable = billiardTableRepository.save(billiardTable);
        return toResponse(
                savedTable,
                resolveReservedTableIds(List.of(savedTable)).contains(savedTable.getId())
        );
    }

    @Transactional(readOnly = true)
    public BilliardTableResponse get(Long id) {
        BilliardTable table = findEntity(id);
        return toResponse(
                table,
                resolveReservedTableIds(List.of(table)).contains(table.getId())
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<BilliardTableResponse> list(
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
        Page<BilliardTable> tables = billiardTableRepository.findAll(buildSpecification(q), pageable);
        Set<Long> reservedTableIds = resolveReservedTableIds(tables.getContent());
        return new PageResponse<>(
                tables.getContent().stream()
                        .map(table -> toResponse(table, reservedTableIds.contains(table.getId())))
                        .toList(),
                tables.getNumber(),
                tables.getSize(),
                tables.getTotalElements(),
                tables.getTotalPages()
        );
    }

    private void applyRequest(BilliardTable billiardTable, BilliardTableUpsertRequest request) {
        billiardTable.setTableType(findTableType(request.tableTypeId()));
        billiardTable.setStatus(request.status());
        billiardTable.setFloorPositionX(request.floorPositionX());
        billiardTable.setFloorPositionY(request.floorPositionY());
        billiardTable.setActive(
                request.active() == null ? billiardTable.isActive() : request.active()
        );
    }

    private BilliardTableResponse toResponse(
            BilliardTable billiardTable,
            boolean hasActiveReservation
    ) {
        TableType tableType = billiardTable.getTableType();
        return new BilliardTableResponse(
                billiardTable.getId(),
                billiardTable.getName(),
                tableType.getId(),
                tableType.getName(),
                resolveStatus(billiardTable.getStatus(), hasActiveReservation),
                billiardTable.getFloorPositionX(),
                billiardTable.getFloorPositionY(),
                billiardTable.isActive(),
                billiardTable.getCreatedAt(),
                billiardTable.getUpdatedAt()
        );
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

    private BilliardTable findEntity(Long id) {
        return billiardTableRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table not found"
        ));
    }

    private TableType findTableType(Long id) {
        return tableTypeRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table type not found"
        ));
    }

    private Specification<BilliardTable> buildSpecification(String q) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(q)) {
                return criteriaBuilder.conjunction();
            }

            query.distinct(true);
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            var tableTypeJoin = root.join("tableType", JoinType.INNER);
            var predicates = new ArrayList<Predicate>();
            predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, '\\'));
            predicates.add(
                    criteriaBuilder.like(criteriaBuilder.lower(tableTypeJoin.get("name")), pattern, '\\')
            );
            predicates.add(
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("status").as(String.class)),
                            pattern,
                            '\\'
                    )
            );
            return criteriaBuilder.or(predicates.toArray(Predicate[]::new));
        };
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String normalizeName(String value) {
        return value.trim();
    }
}
