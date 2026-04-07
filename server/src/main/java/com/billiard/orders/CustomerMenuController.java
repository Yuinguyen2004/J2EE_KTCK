package com.billiard.orders;

import com.billiard.orders.dto.MenuItemResponse;
import com.billiard.shared.web.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/menu-items")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerMenuController {

    private final MenuItemCrudService menuItemCrudService;

    public CustomerMenuController(MenuItemCrudService menuItemCrudService) {
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
        return menuItemCrudService.listActive(q, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    public MenuItemResponse get(@PathVariable Long id) {
        return menuItemCrudService.getActive(id);
    }
}
