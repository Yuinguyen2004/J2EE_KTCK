package com.billiard.tables;

import com.billiard.billing.PricingCalculator;
import com.billiard.customers.AuthenticatedCustomerService;
import com.billiard.customers.Customer;
import com.billiard.memberships.MembershipTier;
import com.billiard.tables.dto.CustomerPricingPreviewResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerPricingPreviewService {

    private final TableTypeRepository tableTypeRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final PricingCalculator pricingCalculator;
    private final AuthenticatedCustomerService authenticatedCustomerService;

    public CustomerPricingPreviewService(
            TableTypeRepository tableTypeRepository,
            PricingRuleRepository pricingRuleRepository,
            PricingCalculator pricingCalculator,
            AuthenticatedCustomerService authenticatedCustomerService
    ) {
        this.tableTypeRepository = tableTypeRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.pricingCalculator = pricingCalculator;
        this.authenticatedCustomerService = authenticatedCustomerService;
    }

    @Transactional(readOnly = true)
    public CustomerPricingPreviewResponse preview(
            Long tableTypeId,
            Integer durationMinutes,
            String customerEmail
    ) {
        if (tableTypeId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Table type is required");
        }
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Duration must be greater than zero minutes"
            );
        }

        TableType tableType = tableTypeRepository.findById(tableTypeId)
                .filter(TableType::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Table type not available"
                ));
        List<PricingRule> pricingRules =
                pricingRuleRepository.findAllByTableType_IdAndActiveTrueOrderBySortOrderAsc(tableTypeId);
        if (pricingRules.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No active pricing rules are configured for this table type"
            );
        }

        Customer customer = authenticatedCustomerService.getRequiredCustomer(customerEmail);
        MembershipTier membershipTier = customer.getMembershipTier();
        var calculation = pricingCalculator.calculate(
                Instant.now(),
                Instant.now().plusSeconds(durationMinutes.longValue() * 60L),
                List.of(),
                pricingRules.stream()
                        .map(rule -> new PricingCalculator.PricingRuleInput(
                                rule.getBlockMinutes(),
                                rule.getPricePerMinute()
                        ))
                        .toList(),
                membershipTier == null ? null : membershipTier.getDiscountPercent()
        );

        return new CustomerPricingPreviewResponse(
                tableType.getId(),
                tableType.getName(),
                durationMinutes,
                membershipTier == null ? java.math.BigDecimal.ZERO : membershipTier.getDiscountPercent(),
                membershipTier == null ? null : membershipTier.getName(),
                calculation.grossAmount(),
                calculation.discountAmount(),
                calculation.netTableAmount()
        );
    }
}
