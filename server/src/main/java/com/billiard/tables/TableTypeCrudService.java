package com.billiard.tables;

import com.billiard.shared.web.PageRequestFactory;
import com.billiard.shared.web.PageResponse;
import com.billiard.tables.dto.TableTypeResponse;
import com.billiard.tables.dto.TableTypeUpsertRequest;
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
public class TableTypeCrudService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "name", "name",
            "active", "active",
            "updatedAt", "updatedAt"
    );

    private final TableTypeRepository tableTypeRepository;

    public TableTypeCrudService(TableTypeRepository tableTypeRepository) {
        this.tableTypeRepository = tableTypeRepository;
    }

    @Transactional
    public TableTypeResponse create(TableTypeUpsertRequest request) {
        String normalizedName = normalizeName(request.name());
        tableTypeRepository.findByNameIgnoreCase(normalizedName).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Table type name already exists");
        });

        TableType tableType = new TableType();
        tableType.setName(normalizedName);
        applyRequest(tableType, request);
        return toResponse(tableTypeRepository.save(tableType));
    }

    @Transactional
    public TableTypeResponse update(Long id, TableTypeUpsertRequest request) {
        TableType tableType = findEntity(id);
        String normalizedName = normalizeName(request.name());
        tableTypeRepository.findByNameIgnoreCase(normalizedName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Table type name already exists"
                    );
                });

        tableType.setName(normalizedName);
        applyRequest(tableType, request);
        return toResponse(tableTypeRepository.save(tableType));
    }

    @Transactional
    public TableTypeResponse updateActive(Long id, boolean active) {
        TableType tableType = findEntity(id);
        tableType.setActive(active);
        return toResponse(tableTypeRepository.save(tableType));
    }

    @Transactional(readOnly = true)
    public TableTypeResponse get(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TableTypeResponse> list(
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
        Page<TableType> tableTypes = tableTypeRepository.findAll(buildSpecification(q), pageable);
        return PageResponse.from(tableTypes, this::toResponse);
    }

    private void applyRequest(TableType tableType, TableTypeUpsertRequest request) {
        tableType.setDescription(normalizeNullable(request.description()));
        tableType.setActive(request.active() == null ? tableType.isActive() : request.active());
    }

    private TableTypeResponse toResponse(TableType tableType) {
        return new TableTypeResponse(
                tableType.getId(),
                tableType.getName(),
                tableType.getDescription(),
                tableType.isActive(),
                tableType.getCreatedAt(),
                tableType.getUpdatedAt()
        );
    }

    private TableType findEntity(Long id) {
        return tableTypeRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Table type not found"
        ));
    }

    private Specification<TableType> buildSpecification(String q) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(q)) {
                return criteriaBuilder.conjunction();
            }

            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            var predicates = new ArrayList<Predicate>();
            predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, '\\'));
            predicates.add(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern, '\\')
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

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
