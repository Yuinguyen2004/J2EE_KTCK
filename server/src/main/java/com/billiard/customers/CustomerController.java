package com.billiard.customers;

import com.billiard.customers.dto.CustomerResponse;
import com.billiard.customers.dto.CustomerUpsertRequest;
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
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class CustomerController {

    private final CustomerCrudService customerCrudService;

    public CustomerController(CustomerCrudService customerCrudService) {
        this.customerCrudService = customerCrudService;
    }

    @GetMapping
    public PageResponse<CustomerResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return customerCrudService.list(q, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return customerCrudService.get(id);
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CustomerUpsertRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerCrudService.create(request));
    }

    @PutMapping("/{id}")
    public CustomerResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CustomerUpsertRequest request
    ) {
        return customerCrudService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public CustomerResponse updateActive(
            @PathVariable Long id,
            @Valid @RequestBody ToggleActiveRequest request
    ) {
        return customerCrudService.updateActive(id, request.active());
    }
}
