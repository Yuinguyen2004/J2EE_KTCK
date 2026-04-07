package com.billiard.memberships;

import com.billiard.memberships.dto.MembershipTierResponse;
import com.billiard.memberships.dto.MembershipTierUpsertRequest;
import com.billiard.shared.web.PageRequestFactory;
import com.billiard.shared.web.PageResponse;
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
public class MembershipTierCrudService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "name", "name",
            "discountPercent", "discountPercent",
            "minimumSpend", "minimumSpend",
            "active", "active",
            "updatedAt", "updatedAt"
    );

    private final MembershipTierRepository membershipTierRepository;

    public MembershipTierCrudService(MembershipTierRepository membershipTierRepository) {
        this.membershipTierRepository = membershipTierRepository;
    }

    @Transactional
    public MembershipTierResponse create(MembershipTierUpsertRequest request) {
        String normalizedName = normalizeName(request.name());
        membershipTierRepository.findByNameIgnoreCase(normalizedName).ifPresent(existing -> {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Membership tier name already exists"
            );
        });

        MembershipTier membershipTier = new MembershipTier();
        applyRequest(membershipTier, request);
        membershipTier.setName(normalizedName);
        return toResponse(membershipTierRepository.save(membershipTier));
    }

    @Transactional
    public MembershipTierResponse update(Long id, MembershipTierUpsertRequest request) {
        MembershipTier membershipTier = findEntity(id);
        String normalizedName = normalizeName(request.name());
        membershipTierRepository.findByNameIgnoreCase(normalizedName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Membership tier name already exists"
                    );
                });

        membershipTier.setName(normalizedName);
        applyRequest(membershipTier, request);
        return toResponse(membershipTierRepository.save(membershipTier));
    }

    @Transactional
    public MembershipTierResponse updateActive(Long id, boolean active) {
        MembershipTier membershipTier = findEntity(id);
        membershipTier.setActive(active);
        return toResponse(membershipTierRepository.save(membershipTier));
    }

    @Transactional(readOnly = true)
    public MembershipTierResponse get(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<MembershipTierResponse> list(
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
        Page<MembershipTier> membershipTiers =
                membershipTierRepository.findAll(buildSpecification(q), pageable);
        return PageResponse.from(membershipTiers, this::toResponse);
    }

    private void applyRequest(MembershipTier membershipTier, MembershipTierUpsertRequest request) {
        membershipTier.setDiscountPercent(request.discountPercent());
        membershipTier.setMinimumSpend(request.minimumSpend());
        membershipTier.setDescription(normalizeNullable(request.description()));
        membershipTier.setActive(
                request.active() == null ? membershipTier.isActive() : request.active()
        );
    }

    private MembershipTierResponse toResponse(MembershipTier membershipTier) {
        return new MembershipTierResponse(
                membershipTier.getId(),
                membershipTier.getName(),
                membershipTier.getDiscountPercent(),
                membershipTier.getMinimumSpend(),
                membershipTier.getDescription(),
                membershipTier.isActive(),
                membershipTier.getCreatedAt(),
                membershipTier.getUpdatedAt()
        );
    }

    private MembershipTier findEntity(Long id) {
        return membershipTierRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Membership tier not found"
                ));
    }

    private Specification<MembershipTier> buildSpecification(String q) {
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
