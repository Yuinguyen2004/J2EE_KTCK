package com.billiard.orders;

import com.billiard.orders.dto.CreateOrderRequest;
import com.billiard.orders.dto.OrderResponse;
import com.billiard.orders.dto.UpdateOrderRequest;
import com.billiard.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/orders")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerOrderController {

    private final OrderService orderService;

    public CustomerOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PageResponse<OrderResponse> list(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication
    ) {
        return orderService.listOwned(
                sessionId,
                q,
                page,
                size,
                sortBy,
                direction,
                authentication.getName()
        );
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id, Authentication authentication) {
        return orderService.getOwned(id, authentication.getName());
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createCustomerDraft(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    public OrderResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderRequest request,
            Authentication authentication
    ) {
        return orderService.updateCustomerDraft(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        orderService.deleteCustomerDraft(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
