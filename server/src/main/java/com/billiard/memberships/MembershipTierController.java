package com.billiard.memberships;

import com.billiard.memberships.dto.MembershipTierResponse;
import com.billiard.memberships.dto.MembershipTierUpsertRequest;
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
@RequestMapping("/api/v1/memberships")
@PreAuthorize("hasRole('ADMIN')")
public class MembershipTierController {

    private final MembershipTierCrudService membershipTierCrudService;

    public MembershipTierController(MembershipTierCrudService membershipTierCrudService) {
        this.membershipTierCrudService = membershipTierCrudService;
    }

    @GetMapping
    public PageResponse<MembershipTierResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return membershipTierCrudService.list(q, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    public MembershipTierResponse get(@PathVariable Long id) {
        return membershipTierCrudService.get(id);
    }

    @PostMapping
    public ResponseEntity<MembershipTierResponse> create(
            @Valid @RequestBody MembershipTierUpsertRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(membershipTierCrudService.create(request));
    }

    @PutMapping("/{id}")
    public MembershipTierResponse update(
            @PathVariable Long id,
            @Valid @RequestBody MembershipTierUpsertRequest request
    ) {
        return membershipTierCrudService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public MembershipTierResponse updateActive(
            @PathVariable Long id,
            @Valid @RequestBody ToggleActiveRequest request
    ) {
        return membershipTierCrudService.updateActive(id, request.active());
    }
}
