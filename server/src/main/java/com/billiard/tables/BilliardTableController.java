package com.billiard.tables;

import com.billiard.shared.web.PageResponse;
import com.billiard.shared.web.ToggleActiveRequest;
import com.billiard.tables.dto.BilliardTableResponse;
import com.billiard.tables.dto.BilliardTableUpsertRequest;
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
@RequestMapping("/api/v1/tables")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class BilliardTableController {

    private final BilliardTableCrudService billiardTableCrudService;

    public BilliardTableController(BilliardTableCrudService billiardTableCrudService) {
        this.billiardTableCrudService = billiardTableCrudService;
    }

    @GetMapping
    public PageResponse<BilliardTableResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return billiardTableCrudService.list(q, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    public BilliardTableResponse get(@PathVariable Long id) {
        return billiardTableCrudService.get(id);
    }

    @PostMapping
    public ResponseEntity<BilliardTableResponse> create(
            @Valid @RequestBody BilliardTableUpsertRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(billiardTableCrudService.create(request));
    }

    @PutMapping("/{id}")
    public BilliardTableResponse update(
            @PathVariable Long id,
            @Valid @RequestBody BilliardTableUpsertRequest request
    ) {
        return billiardTableCrudService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public BilliardTableResponse updateActive(
            @PathVariable Long id,
            @Valid @RequestBody ToggleActiveRequest request
    ) {
        return billiardTableCrudService.updateActive(id, request.active());
    }
}
