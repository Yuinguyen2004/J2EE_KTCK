package com.billiard.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.billiard.customers.Customer;
import com.billiard.memberships.MembershipTier;
import com.billiard.orders.Order;
import com.billiard.orders.OrderStatus;
import com.billiard.orders.OrderRepository;
import com.billiard.tables.BilliardTable;
import com.billiard.tables.PricingRule;
import com.billiard.tables.PricingRuleRepository;
import com.billiard.tables.TableStatus;
import com.billiard.tables.TableType;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private TableSessionRepository tableSessionRepository;

    @Mock
    private SessionPauseRepository sessionPauseRepository;

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    private MutableClock clock;
    private InvoiceService invoiceService;

    private User staff;
    private Customer customer;
    private TableSession completedSession;
    private final List<Invoice> storedInvoices = new ArrayList<>();
    private final AtomicLong invoiceIdSequence = new AtomicLong(900L);

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-03-28T10:00:00Z"), ZoneId.of("UTC"));
        invoiceService = new InvoiceService(
                invoiceRepository,
                tableSessionRepository,
                sessionPauseRepository,
                pricingRuleRepository,
                orderRepository,
                userRepository,
                new PricingCalculator(),
                clock
        );

        staff = buildUser(10L, "staff@example.com", "Floor Staff", UserRole.STAFF);
        customer = buildCustomer(20L, "customer@example.com", "Customer One", "10.00");
        completedSession = buildCompletedSession(100L, customer, staff);

        lenient().when(tableSessionRepository.findById(completedSession.getId()))
                .thenReturn(Optional.of(completedSession));
        lenient().when(tableSessionRepository.save(any(TableSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userRepository.findByEmailIgnoreCase(staff.getEmail()))
                .thenReturn(Optional.of(staff));
        lenient().when(invoiceRepository.findBySession_Id(completedSession.getId())).thenAnswer(invocation ->
                storedInvoices.stream()
                        .filter(invoice -> invoice.getSession().getId().equals(completedSession.getId()))
                        .findFirst()
        );
        lenient().when(invoiceRepository.findById(any(Long.class))).thenAnswer(invocation -> {
            Long invoiceId = invocation.getArgument(0);
            return storedInvoices.stream()
                    .filter(invoice -> invoice.getId().equals(invoiceId))
                    .findFirst();
        });
        lenient().when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            if (invoice.getId() == null) {
                invoice.setId(invoiceIdSequence.getAndIncrement());
                invoice.setCreatedAt(clock.instant());
                storedInvoices.add(invoice);
            }
            invoice.setUpdatedAt(clock.instant());
            return invoice;
        });
    }

    @Test
    void generateCreatesDraftInvoiceFromSessionOrdersAndDiscount() {
        when(sessionPauseRepository.findAllBySession_IdOrderByStartedAtAsc(completedSession.getId()))
                .thenReturn(List.of());
        when(pricingRuleRepository.findAllByTableType_IdAndActiveTrueOrderBySortOrderAsc(any(Long.class)))
                .thenReturn(List.of(
                        buildPricingRule(15, "2000.00", 0),
                        buildPricingRule(15, "1000.00", 1)
                ));
        when(orderRepository.findBillableBySessionId(completedSession.getId()))
                .thenReturn(List.of(buildOrder(completedSession, "12000.00")));

        var invoice = invoiceService.generateForSession(completedSession.getId());

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(invoice.tableAmount()).isEqualByComparingTo("60000.00");
        assertThat(invoice.orderAmount()).isEqualByComparingTo("12000.00");
        assertThat(invoice.discountAmount()).isEqualByComparingTo("6000.00");
        assertThat(invoice.totalAmount()).isEqualByComparingTo("66000.00");
        assertThat(completedSession.getTotalAmount()).isEqualByComparingTo("66000.00");
    }

    @Test
    void generateRejectsActiveSessions() {
        completedSession.setStatus(SessionStatus.ACTIVE);
        completedSession.setEndedAt(null);

        assertThatThrownBy(() -> invoiceService.generateForSession(completedSession.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void issueAndPayAdvanceInvoiceStatus() {
        Invoice invoice = storeDraftInvoice();

        var issued = invoiceService.issue(invoice.getId(), staff.getEmail());
        clock.advance(Duration.ofMinutes(2));
        var paid = invoiceService.pay(invoice.getId(), staff.getEmail());

        assertThat(issued.status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(issued.issuedById()).isEqualTo(staff.getId());
        assertThat(issued.issuedAt()).isEqualTo(Instant.parse("2026-03-28T10:00:00Z"));
        assertThat(paid.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(paid.paidAt()).isEqualTo(Instant.parse("2026-03-28T10:02:00Z"));
    }

    @Test
    void voidRejectsPaidInvoices() {
        Invoice invoice = storeDraftInvoice();
        invoiceService.issue(invoice.getId(), staff.getEmail());
        invoiceService.pay(invoice.getId(), staff.getEmail());

        assertThatThrownBy(() -> invoiceService.voidInvoice(invoice.getId(), staff.getEmail()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void generateIgnoresPendingOrdersWhenCalculatingInvoice() {
        when(sessionPauseRepository.findAllBySession_IdOrderByStartedAtAsc(completedSession.getId()))
                .thenReturn(List.of());
        when(pricingRuleRepository.findAllByTableType_IdAndActiveTrueOrderBySortOrderAsc(any(Long.class)))
                .thenReturn(List.of(buildPricingRule(15, "2000.00", 0)));
        when(orderRepository.findBillableBySessionId(completedSession.getId()))
                .thenReturn(List.of(buildOrder(completedSession, "12000.00", OrderStatus.CONFIRMED)));

        var invoice = invoiceService.generateForSession(completedSession.getId());

        assertThat(invoice.orderAmount()).isEqualByComparingTo("12000.00");
        assertThat(invoice.totalAmount()).isEqualByComparingTo("93000.00");
    }

    private Invoice storeDraftInvoice() {
        Invoice invoice = new Invoice();
        invoice.setSession(completedSession);
        invoice.setCustomer(customer);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setTableAmount(new BigDecimal("60000.00"));
        invoice.setOrderAmount(new BigDecimal("12000.00"));
        invoice.setDiscountAmount(new BigDecimal("6000.00"));
        invoice.setTotalAmount(new BigDecimal("66000.00"));
        return invoiceRepository.save(invoice);
    }

    private static User buildUser(Long id, String email, String fullName, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    private static Customer buildCustomer(
            Long id,
            String email,
            String fullName,
            String discountPercent
    ) {
        MembershipTier membershipTier = new MembershipTier();
        membershipTier.setId(30L);
        membershipTier.setName("VIP");
        membershipTier.setDiscountPercent(new BigDecimal(discountPercent));
        membershipTier.setActive(true);

        Customer customer = new Customer();
        customer.setId(id);
        customer.setUser(buildUser(40L, email, fullName, UserRole.CUSTOMER));
        customer.setMembershipTier(membershipTier);
        return customer;
    }

    private static TableSession buildCompletedSession(Long id, Customer customer, User staff) {
        TableType tableType = new TableType();
        tableType.setId(50L);
        tableType.setName("Pool");
        tableType.setActive(true);

        BilliardTable table = new BilliardTable();
        table.setId(60L);
        table.setName("Table 6");
        table.setTableType(tableType);
        table.setStatus(TableStatus.AVAILABLE);
        table.setActive(true);

        TableSession session = new TableSession();
        session.setId(id);
        session.setTable(table);
        session.setCustomer(customer);
        session.setStaff(staff);
        session.setStatus(SessionStatus.COMPLETED);
        session.setStartedAt(Instant.parse("2026-03-28T09:00:00Z"));
        session.setEndedAt(Instant.parse("2026-03-28T09:45:00Z"));
        session.setTotalPausedSeconds(0L);
        session.setTotalAmount(BigDecimal.ZERO);
        return session;
    }

    private static PricingRule buildPricingRule(
            Integer blockMinutes,
            String pricePerMinute,
            Integer sortOrder
    ) {
        PricingRule pricingRule = new PricingRule();
        pricingRule.setBlockMinutes(blockMinutes);
        pricingRule.setPricePerMinute(new BigDecimal(pricePerMinute));
        pricingRule.setSortOrder(sortOrder);
        pricingRule.setActive(true);
        return pricingRule;
    }

    private static Order buildOrder(TableSession session, String totalAmount) {
        return buildOrder(session, totalAmount, OrderStatus.CONFIRMED);
    }

    private static Order buildOrder(TableSession session, String totalAmount, OrderStatus status) {
        Order order = new Order();
        order.setId(70L);
        order.setSession(session);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal(totalAmount));
        order.setOrderedAt(session.getEndedAt());
        return order;
    }

    private static final class MutableClock extends Clock {

        private Instant currentInstant;
        private final ZoneId zoneId;

        private MutableClock(Instant currentInstant, ZoneId zoneId) {
            this.currentInstant = currentInstant;
            this.zoneId = zoneId;
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(currentInstant, zone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }

        private void advance(Duration duration) {
            currentInstant = currentInstant.plus(duration);
        }
    }
}
