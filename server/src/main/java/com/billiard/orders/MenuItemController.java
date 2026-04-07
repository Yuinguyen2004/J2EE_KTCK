package com.billiard.orders;

import com.billiard.orders.dto.MenuItemResponse;
import com.billiard.orders.dto.MenuItemUpsertRequest;
import com.billiard.shared.web.PageResponse;
import com.billiard.shared.web.ToggleActiveRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menu-items")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class MenuItemController {

    private final MenuItemCrudService menuItemCrudService;

    public MenuItemController(MenuItemCrudService menuItemCrudService) {
        this.menuItemCrudService = menuItemCrudService;
    }

    @GetMapping
    public PageResponse<MenuItemResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return menuItemCrudService.list(q, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    public MenuItemResponse get(@PathVariable Long id) {
        return menuItemCrudService.get(id);
    }

    @PostMapping
    public ResponseEntity<MenuItemResponse> create(
            @Valid @RequestBody MenuItemUpsertRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuItemCrudService.create(request));
    }

    @PutMapping("/{id}")
    public MenuItemResponse update(
            @PathVariable Long id,
            @Valid @RequestBody MenuItemUpsertRequest request
    ) {
        return menuItemCrudService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public MenuItemResponse updateActive(
            @PathVariable Long id,
            @Valid @RequestBody ToggleActiveRequest request
    ) {
        return menuItemCrudService.updateActive(id, request.active());
    }
}
