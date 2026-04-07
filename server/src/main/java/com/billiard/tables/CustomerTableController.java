package com.billiard.tables;

import com.billiard.shared.web.PageResponse;
import com.billiard.tables.dto.CustomerTableAvailabilityResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/tables")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerTableController {

    private final CustomerTableService customerTableService;
    private final CustomerPricingPreviewService customerPricingPreviewService;

    public CustomerTableController(
            CustomerTableService customerTableService,
            CustomerPricingPreviewService customerPricingPreviewService
    ) {
        this.customerTableService = customerTableService;
        this.customerPricingPreviewService = customerPricingPreviewService;
    }

    @GetMapping
    public PageResponse<CustomerTableAvailabilityResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return customerTableService.list(q, page, size, sortBy, direction);
    }

    @GetMapping("/pricing-preview")
    public com.billiard.tables.dto.CustomerPricingPreviewResponse preview(
            @RequestParam Long tableTypeId,
            @RequestParam Integer durationMinutes,
            Authentication authentication
    ) {
        return customerPricingPreviewService.preview(
                tableTypeId,
                durationMinutes,
                authentication.getName()
        );
    }
}
