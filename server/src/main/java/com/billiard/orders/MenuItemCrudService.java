package com.billiard.orders;

import com.billiard.orders.dto.MenuItemResponse;
import com.billiard.orders.dto.MenuItemUpsertRequest;
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
public class MenuItemCrudService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "name", "name",
            "price", "price",
            "active", "active",
            "updatedAt", "updatedAt"
    );

    private final MenuItemRepository menuItemRepository;

    public MenuItemCrudService(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    @Transactional
    public MenuItemResponse create(MenuItemUpsertRequest request) {
        String normalizedName = normalizeName(request.name());
        menuItemRepository.findByNameIgnoreCase(normalizedName).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Menu item name already exists");
        });

        MenuItem menuItem = new MenuItem();
        menuItem.setName(normalizedName);
        applyRequest(menuItem, request);
        return toResponse(menuItemRepository.save(menuItem));
    }

    @Transactional
    public MenuItemResponse update(Long id, MenuItemUpsertRequest request) {
        MenuItem menuItem = findEntity(id);
        String normalizedName = normalizeName(request.name());
        menuItemRepository.findByNameIgnoreCase(normalizedName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Menu item name already exists"
                    );
                });

        menuItem.setName(normalizedName);
        applyRequest(menuItem, request);
        return toResponse(menuItemRepository.save(menuItem));
    }

    @Transactional
    public MenuItemResponse updateActive(Long id, boolean active) {
        MenuItem menuItem = findEntity(id);
        menuItem.setActive(active);
        return toResponse(menuItemRepository.save(menuItem));
    }

    @Transactional(readOnly = true)
    public MenuItemResponse get(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public MenuItemResponse getActive(Long id) {
        MenuItem menuItem = findEntity(id);
        if (!menuItem.isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found");
        }
        return toResponse(menuItem);
    }

    @Transactional(readOnly = true)
    public PageResponse<MenuItemResponse> list(
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
        Page<MenuItem> menuItems = menuItemRepository.findAll(buildSpecification(q), pageable);
        return PageResponse.from(menuItems, this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<MenuItemResponse> listActive(
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
        Page<MenuItem> menuItems = menuItemRepository.findAll(buildSpecification(q, true), pageable);
        return PageResponse.from(menuItems, this::toResponse);
    }

    private void applyRequest(MenuItem menuItem, MenuItemUpsertRequest request) {
        menuItem.setDescription(normalizeNullable(request.description()));
        menuItem.setPrice(request.price());
        menuItem.setImageUrl(normalizeNullable(request.imageUrl()));
        menuItem.setActive(request.active() == null ? menuItem.isActive() : request.active());
    }

    private MenuItemResponse toResponse(MenuItem menuItem) {
        return new MenuItemResponse(
                menuItem.getId(),
                menuItem.getName(),
                menuItem.getDescription(),
                menuItem.getPrice(),
                menuItem.getImageUrl(),
                menuItem.isActive(),
                menuItem.getCreatedAt(),
                menuItem.getUpdatedAt()
        );
    }

    private MenuItem findEntity(Long id) {
        return menuItemRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Menu item not found"
        ));
    }

    private Specification<MenuItem> buildSpecification(String q) {
        return buildSpecification(q, false);
    }

    private Specification<MenuItem> buildSpecification(String q, boolean activeOnly) {
        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();
            if (activeOnly) {
                predicates.add(criteriaBuilder.isTrue(root.get("active")));
            }

            if (!StringUtils.hasText(q)) {
                return predicates.isEmpty()
                        ? criteriaBuilder.conjunction()
                        : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
            }

            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            Predicate searchPredicate = criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern, '\\')
            );
            predicates.add(searchPredicate);
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
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
