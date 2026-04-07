package com.billiard.tables;

import com.billiard.shared.web.PageResponse;
import com.billiard.shared.web.ToggleActiveRequest;
import com.billiard.tables.dto.PricingRuleResponse;
import com.billiard.tables.dto.PricingRuleUpsertRequest;
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
@RequestMapping("/api/v1/pricing-rules")
public class PricingRuleController {

    private final PricingRuleCrudService pricingRuleCrudService;

    public PricingRuleController(PricingRuleCrudService pricingRuleCrudService) {
        this.pricingRuleCrudService = pricingRuleCrudService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public PageResponse<PricingRuleResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return pricingRuleCrudService.list(q, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public PricingRuleResponse get(@PathVariable Long id) {
        return pricingRuleCrudService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PricingRuleResponse> create(
            @Valid @RequestBody PricingRuleUpsertRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pricingRuleCrudService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PricingRuleResponse update(
            @PathVariable Long id,
            @Valid @RequestBody PricingRuleUpsertRequest request
    ) {
        return pricingRuleCrudService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public PricingRuleResponse updateActive(
            @PathVariable Long id,
            @Valid @RequestBody ToggleActiveRequest request
    ) {
        return pricingRuleCrudService.updateActive(id, request.active());
    }
}
