package com.billiard.billing;

import com.billiard.billing.PricingCalculator.PauseWindow;
import com.billiard.billing.PricingCalculator.PricingCalculationResult;
import com.billiard.billing.PricingCalculator.PricingRuleInput;
import com.billiard.billing.dto.InvoiceResponse;
import com.billiard.orders.Order;
import com.billiard.orders.OrderRepository;
import com.billiard.shared.web.PageRequestFactory;
import com.billiard.shared.web.PageResponse;
import com.billiard.tables.PricingRule;
import com.billiard.tables.PricingRuleRepository;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
public class InvoiceService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "issuedAt", "issuedAt",
            "paidAt", "paidAt",
            "totalAmount", "totalAmount",
            "status", "status",
            "tableName", "session.table.name",
            "customerName", "customer.user.fullName"
    );

    private final InvoiceRepository invoiceRepository;
    private final TableSessionRepository tableSessionRepository;
    private final SessionPauseRepository sessionPauseRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PricingCalculator pricingCalculator;
    private final Clock clock;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            TableSessionRepository tableSessionRepository,
            SessionPauseRepository sessionPauseRepository,
            PricingRuleRepository pricingRuleRepository,
            OrderRepository orderRepository,
            UserRepository userRepository,
            PricingCalculator pricingCalculator,
            Clock clock
    ) {
        this.invoiceRepository = invoiceRepository;
        this.tableSessionRepository = tableSessionRepository;
        this.sessionPauseRepository = sessionPauseRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.pricingCalculator = pricingCalculator;
        this.clock = clock;
    }

    @Transactional
    public InvoiceResponse generateForSession(Long sessionId) {
        TableSession session = findCompletedSession(sessionId);
        Invoice invoice = invoiceRepository.findBySession_Id(sessionId)
                .map(this::ensureDraftInvoice)
                .orElseGet(() -> buildDraftInvoice(session));

        PricingCalculationResult pricing = pricingCalculator.calculate(
                session.getStartedAt(),
                session.getEndedAt(),
                loadPauseWindows(sessionId),
                loadPricingRules(session),
                resolveDiscountPercent(session)
        );
        BigDecimal orderAmount = orderRepository.findBillableBySessionId(sessionId)
                .stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subtotalAmount = pricing.grossAmount().add(orderAmount);
        BigDecimal discountAmount = calculateSubtotalDiscount(
                subtotalAmount,
                resolveDiscountPercent(session)
        );

        invoice.setCustomer(session.getCustomer());
        invoice.setTableAmount(pricing.grossAmount());
        invoice.setOrderAmount(orderAmount);
        invoice.setDiscountAmount(discountAmount);
        invoice.setTotalAmount(subtotalAmount.subtract(discountAmount));

        session.setTotalAmount(invoice.getTotalAmount());
        tableSessionRepository.save(session);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse issue(Long invoiceId, String staffEmail) {
        Invoice invoice = findInvoice(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only draft invoices can be issued"
            );
        }

        User staff = findStaff(staffEmail);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedBy(staff);
        invoice.setIssuedAt(Instant.now(clock));
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse pay(Long invoiceId, String staffEmail) {
        Invoice invoice = findInvoice(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only issued invoices can be paid"
            );
        }

        findStaff(staffEmail);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(Instant.now(clock));
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse voidInvoice(Long invoiceId, String staffEmail) {
        Invoice invoice = findInvoice(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.VOID) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only draft or issued invoices can be voided"
            );
        }

        findStaff(staffEmail);
        invoice.setStatus(InvoiceStatus.VOID);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long invoiceId) {
        return toResponse(findInvoice(invoiceId));
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> list(
            Long sessionId,
            InvoiceStatus status,
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
        Page<Invoice> invoices = invoiceRepository.findAll(
                buildSpecification(sessionId, status, q),
                pageable
        );
        return PageResponse.from(invoices, this::toResponse);
    }

    private Invoice buildDraftInvoice(TableSession session) {
        Invoice invoice = new Invoice();
        invoice.setSession(session);
        invoice.setCustomer(session.getCustomer());
        invoice.setStatus(InvoiceStatus.DRAFT);
        return invoice;
    }

    private Invoice ensureDraftInvoice(Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only draft invoices can be regenerated"
            );
        }
        return invoice;
    }

    private List<PauseWindow> loadPauseWindows(Long sessionId) {
        return sessionPauseRepository.findAllBySession_IdOrderByStartedAtAsc(sessionId).stream()
                .map(pause -> new PauseWindow(pause.getStartedAt(), pause.getEndedAt()))
                .toList();
    }

    private List<PricingRuleInput> loadPricingRules(TableSession session) {
        List<PricingRule> pricingRules = pricingRuleRepository
                .findAllByTableType_IdAndActiveTrueOrderBySortOrderAsc(
                        session.getTable().getTableType().getId()
                );
        if (pricingRules.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No active pricing rules are configured for this table type"
            );
        }

        return pricingRules.stream()
                .map(rule -> new PricingRuleInput(rule.getBlockMinutes(), rule.getPricePerMinute()))
                .toList();
    }

    private BigDecimal resolveDiscountPercent(TableSession session) {
        if (session.getCustomer() == null || session.getCustomer().getMembershipTier() == null) {
            return BigDecimal.ZERO;
        }
        return session.getCustomer().getMembershipTier().getDiscountPercent();
    }

    private BigDecimal calculateSubtotalDiscount(
            BigDecimal subtotalAmount,
            BigDecimal discountPercent
    ) {
        if (subtotalAmount == null || discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return subtotalAmount
                .multiply(discountPercent)
                .divide(BigDecimal.valueOf(100L), 2, RoundingMode.HALF_UP);
    }

    private TableSession findCompletedSession(Long sessionId) {
        TableSession session = tableSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found"
                ));

        if (session.getStatus() != SessionStatus.COMPLETED || session.getEndedAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invoices can only be generated for completed sessions"
            );
        }

        return session;
    }

    private Invoice findInvoice(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Invoice not found"
                ));
    }

    private User findStaff(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.getRole() != UserRole.CUSTOMER)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Only staff can manage invoices"
                ));
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        TableSession session = invoice.getSession();
        User issuedBy = invoice.getIssuedBy();
        var customer = invoice.getCustomer();

        return new InvoiceResponse(
                invoice.getId(),
                session.getId(),
                session.getTable().getId(),
                session.getTable().getName(),
                customer == null ? null : customer.getId(),
                customer == null ? null : customer.getUser().getFullName(),
                issuedBy == null ? null : issuedBy.getId(),
                issuedBy == null ? null : issuedBy.getFullName(),
                invoice.getStatus(),
                invoice.getTableAmount(),
                invoice.getOrderAmount(),
                invoice.getDiscountAmount(),
                invoice.getTotalAmount(),
                invoice.getIssuedAt(),
                invoice.getPaidAt(),
                invoice.getNotes(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }

    private Specification<Invoice> buildSpecification(
            Long sessionId,
            InvoiceStatus status,
            String q
    ) {
        return (root, query, criteriaBuilder) -> {
            if (query != null) {
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();
            if (sessionId != null) {
                predicates.add(criteriaBuilder.equal(root.get("session").get("id"), sessionId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(q)) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                var sessionJoin = root.join("session", JoinType.INNER);
                var tableJoin = sessionJoin.join("table", JoinType.INNER);
                var customerJoin = root.join("customer", JoinType.LEFT);
                var customerUserJoin = customerJoin.join("user", JoinType.LEFT);
                var issuedByJoin = root.join("issuedBy", JoinType.LEFT);

                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("notes")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(tableJoin.get("name")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(customerUserJoin.get("fullName")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(issuedByJoin.get("fullName")), pattern, '\\')
                ));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
