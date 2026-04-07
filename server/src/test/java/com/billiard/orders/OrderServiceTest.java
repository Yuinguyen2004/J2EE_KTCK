package com.billiard.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.billiard.billing.SessionStatus;
import com.billiard.billing.TableSession;
import com.billiard.billing.TableSessionRepository;
import com.billiard.customers.AuthenticatedCustomerService;
import com.billiard.customers.Customer;
import com.billiard.memberships.MembershipTier;
import com.billiard.orders.dto.CreateOrderItemRequest;
import com.billiard.orders.dto.CreateOrderRequest;
import com.billiard.orders.dto.UpdateOrderRequest;
import com.billiard.shared.web.PageResponse;
import com.billiard.tables.BilliardTable;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private TableSessionRepository tableSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticatedCustomerService authenticatedCustomerService;

    private MutableClock clock;
    private OrderService orderService;

    private User staff;
    private Customer customer;
    private Customer otherCustomer;
    private TableSession activeSession;
    private MenuItem cola;
    private MenuItem chips;
    private final AtomicLong orderIdSequence = new AtomicLong(500L);
    private final AtomicLong orderItemIdSequence = new AtomicLong(800L);
    private final Map<Long, Order> storedOrders = new LinkedHashMap<>();
    private final Map<Long, List<OrderItem>> storedItemsByOrderId = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-03-28T08:00:00Z"), ZoneId.of("UTC"));
        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                menuItemRepository,
                tableSessionRepository,
                userRepository,
                authenticatedCustomerService,
                clock
        );

        staff = buildUser(10L, "staff@example.com", "Shift Lead", UserRole.STAFF);
        customer = buildCustomer(20L, "customer@example.com", "Customer One");
        otherCustomer = buildCustomer(21L, "other@example.com", "Customer Two");
        activeSession = buildSession(100L, SessionStatus.ACTIVE, customer, staff);
        cola = buildMenuItem(200L, "Cola", "2.50", true);
        chips = buildMenuItem(201L, "Chips", "4.00", true);

        lenient().when(userRepository.findByEmailIgnoreCase(staff.getEmail()))
                .thenReturn(Optional.of(staff));
        lenient().when(tableSessionRepository.findById(activeSession.getId()))
                .thenReturn(Optional.of(activeSession));
        lenient().when(tableSessionRepository.findByIdForUpdate(activeSession.getId()))
                .thenReturn(Optional.of(activeSession));
        lenient().when(authenticatedCustomerService.getRequiredCustomer(customer.getUser().getEmail()))
                .thenReturn(customer);
        lenient().when(authenticatedCustomerService.getRequiredCustomer(otherCustomer.getUser().getEmail()))
                .thenReturn(otherCustomer);
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(orderIdSequence.getAndIncrement());
                order.setCreatedAt(clock.instant());
            }
            order.setUpdatedAt(clock.instant());
            storedOrders.put(order.getId(), order);
            return order;
        });
        lenient().when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<OrderItem> items = (List<OrderItem>) invocation.getArgument(0);
            List<OrderItem> savedItems = new ArrayList<>();
            for (OrderItem item : items) {
                if (item.getId() == null) {
                    item.setId(orderItemIdSequence.getAndIncrement());
                    item.setCreatedAt(clock.instant());
                }
                item.setUpdatedAt(clock.instant());
                savedItems.add(item);
            }
            if (!savedItems.isEmpty()) {
                storedItemsByOrderId.put(savedItems.getFirst().getOrder().getId(), savedItems);
            }
            return savedItems;
        });
        lenient().when(orderRepository.findById(any(Long.class))).thenAnswer(invocation -> {
            Long orderId = invocation.getArgument(0);
            return Optional.ofNullable(storedOrders.get(orderId));
        });
        lenient().when(orderRepository.findByIdForUpdate(any(Long.class))).thenAnswer(invocation -> {
            Long orderId = invocation.getArgument(0);
            return Optional.ofNullable(storedOrders.get(orderId));
        });
        lenient().when(orderItemRepository.findAllByOrder_Id(any(Long.class))).thenAnswer(invocation -> {
            Long orderId = invocation.getArgument(0);
            return storedItemsByOrderId.getOrDefault(orderId, List.of());
        });
        lenient().when(orderItemRepository.findAllByOrder_IdIn(anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Long> orderIds = invocation.getArgument(0);
            return orderIds.stream()
                    .flatMap(orderId -> storedItemsByOrderId.getOrDefault(orderId, List.of()).stream())
                    .toList();
        });
    }

    @Test
    void createSnapshotsMenuPricesAndTotals() {
        when(menuItemRepository.findAllById(List.of(cola.getId(), chips.getId())))
                .thenReturn(List.of(cola, chips));

        var response = orderService.create(
                new CreateOrderRequest(
                        activeSession.getId(),
                        List.of(
                                new CreateOrderItemRequest(cola.getId(), 2),
                                new CreateOrderItemRequest(chips.getId(), 1)
                        ),
                        "  table-side snacks  "
                ),
                staff.getEmail()
        );

        assertThat(response.sessionId()).isEqualTo(activeSession.getId());
        assertThat(response.staffId()).isEqualTo(staff.getId());
        assertThat(response.customerId()).isEqualTo(customer.getId());
        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.totalAmount()).isEqualByComparingTo("9.00");
        assertThat(response.orderedAt()).isEqualTo(clock.instant());
        assertThat(response.notes()).isEqualTo("table-side snacks");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().unitPrice()).isEqualByComparingTo("2.50");
        assertThat(response.items().getFirst().subtotal()).isEqualByComparingTo("5.00");
        assertThat(response.items().get(1).subtotal()).isEqualByComparingTo("4.00");
        assertThat(storedOrders.get(response.id()).getTotalAmount()).isEqualByComparingTo("9.00");
    }

    @Test
    void createRejectsCompletedSession() {
        activeSession.setStatus(SessionStatus.COMPLETED);

        assertThatThrownBy(() -> orderService.create(
                new CreateOrderRequest(
                        activeSession.getId(),
                        List.of(new CreateOrderItemRequest(cola.getId(), 1)),
                        null
                ),
                staff.getEmail()
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createRejectsInactiveMenuItems() {
        chips.setActive(false);
        when(menuItemRepository.findAllById(List.of(chips.getId()))).thenReturn(List.of(chips));

        assertThatThrownBy(() -> orderService.create(
                new CreateOrderRequest(
                        activeSession.getId(),
                        List.of(new CreateOrderItemRequest(chips.getId(), 1)),
                        null
                ),
                staff.getEmail()
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRejectsDuplicateMenuItemsInOneOrder() {
        assertThatThrownBy(() -> orderService.create(
                new CreateOrderRequest(
                        activeSession.getId(),
                        List.of(
                                new CreateOrderItemRequest(cola.getId(), 1),
                                new CreateOrderItemRequest(cola.getId(), 3)
                        ),
                        null
                ),
                staff.getEmail()
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listReturnsPagedOrdersWithItems() {
        when(menuItemRepository.findAllById(List.of(cola.getId()))).thenReturn(List.of(cola));
        var created = orderService.create(
                new CreateOrderRequest(
                        activeSession.getId(),
                        List.of(new CreateOrderItemRequest(cola.getId(), 2)),
                        "cola round"
                ),
                staff.getEmail()
        );

        Order storedOrder = storedOrders.get(created.id());
        when(orderRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(storedOrder), PageRequest.of(0, 20), 1));

        PageResponse<?> page = orderService.list(activeSession.getId(), "cola", 0, 20, "orderedAt", "DESC");

        assertThat(page.items()).hasSize(1);
        var first = (com.billiard.orders.dto.OrderResponse) page.items().getFirst();
        assertThat(first.id()).isEqualTo(created.id());
        assertThat(first.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(first.items()).hasSize(1);
        assertThat(first.items().getFirst().menuItemName()).isEqualTo("Cola");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void createCustomerDraftCreatesPendingOrderWithoutStaffAssignment() {
        when(menuItemRepository.findAllById(List.of(cola.getId()))).thenReturn(List.of(cola));

        var response = orderService.createCustomerDraft(
                new CreateOrderRequest(
                        activeSession.getId(),
                        List.of(new CreateOrderItemRequest(cola.getId(), 1)),
                        "customer draft"
                ),
                customer.getUser().getEmail()
        );

        assertThat(response.customerId()).isEqualTo(customer.getId());
        assertThat(response.staffId()).isNull();
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo("2.50");
    }

    @Test
    void updateCustomerDraftReplacesItemsWhileOrderIsPending() {
        when(menuItemRepository.findAllById(List.of(cola.getId()))).thenReturn(List.of(cola));
        var created = orderService.createCustomerDraft(
                new CreateOrderRequest(
                        activeSession.getId(),
                        List.of(new CreateOrderItemRequest(cola.getId(), 1)),
                        "first pass"
                ),
                customer.getUser().getEmail()
        );

        when(menuItemRepository.findAllById(List.of(chips.getId()))).thenReturn(List.of(chips));

        var updated = orderService.updateCustomerDraft(
                created.id(),
                new UpdateOrderRequest(
                        List.of(new CreateOrderItemRequest(chips.getId(), 2)),
                        "updated draft"
                ),
                customer.getUser().getEmail()
        );

        assertThat(updated.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(updated.items()).hasSize(1);
        assertThat(updated.items().getFirst().menuItemName()).isEqualTo("Chips");
        assertThat(updated.totalAmount()).isEqualByComparingTo("8.00");
        assertThat(updated.notes()).isEqualTo("updated draft");
    }

    @Test
    void confirmTurnsPendingDraftIntoConfirmedOrder() {
        when(menuItemRepository.findAllById(List.of(cola.getId()))).thenReturn(List.of(cola));
        var created = orderService.createCustomerDraft(
                new CreateOrderRequest(
                        activeSession.getId(),
                        List.of(new CreateOrderItemRequest(cola.getId(), 1)),
                        null
                ),
                customer.getUser().getEmail()
        );

        var confirmed = orderService.confirm(created.id(), staff.getEmail());

        assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(confirmed.staffId()).isEqualTo(staff.getId());
    }

    @Test
    void getOwnedRejectsAccessToAnotherCustomersOrder() {
        when(menuItemRepository.findAllById(List.of(cola.getId()))).thenReturn(List.of(cola));
        var created = orderService.createCustomerDraft(
                new CreateOrderRequest(
                        activeSession.getId(),
                        List.of(new CreateOrderItemRequest(cola.getId(), 1)),
                        null
                ),
                customer.getUser().getEmail()
        );

        assertThatThrownBy(() -> orderService.getOwned(created.id(), otherCustomer.getUser().getEmail()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateCustomerDraftRejectsConfirmedOrders() {
        when(menuItemRepository.findAllById(List.of(cola.getId()))).thenReturn(List.of(cola));
        var created = orderService.createCustomerDraft(
                new CreateOrderRequest(
                        activeSession.getId(),
                        List.of(new CreateOrderItemRequest(cola.getId(), 1)),
                        null
                ),
                customer.getUser().getEmail()
        );
        orderService.confirm(created.id(), staff.getEmail());

        assertThatThrownBy(() -> orderService.updateCustomerDraft(
                created.id(),
                new UpdateOrderRequest(
                        List.of(new CreateOrderItemRequest(cola.getId(), 2)),
                        "late edit"
                ),
                customer.getUser().getEmail()
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
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

    private static Customer buildCustomer(Long id, String email, String fullName) {
        User user = buildUser(30L, email, fullName, UserRole.CUSTOMER);
        MembershipTier membershipTier = new MembershipTier();
        membershipTier.setId(40L);
        membershipTier.setName("Silver");
        membershipTier.setActive(true);

        Customer customer = new Customer();
        customer.setId(id);
        customer.setUser(user);
        customer.setMembershipTier(membershipTier);
        return customer;
    }

    private static TableSession buildSession(
            Long id,
            SessionStatus status,
            Customer customer,
            User staff
    ) {
        TableType tableType = new TableType();
        tableType.setId(2L);
        tableType.setName("Pool");
        tableType.setActive(true);

        BilliardTable table = new BilliardTable();
        table.setId(3L);
        table.setName("Table 3");
        table.setTableType(tableType);
        table.setStatus(TableStatus.IN_USE);
        table.setActive(true);

        TableSession session = new TableSession();
        session.setId(id);
        session.setTable(table);
        session.setCustomer(customer);
        session.setStaff(staff);
        session.setStatus(status);
        session.setStartedAt(Instant.parse("2026-03-28T07:30:00Z"));
        session.setTotalAmount(BigDecimal.ZERO);
        return session;
    }

    private static MenuItem buildMenuItem(Long id, String name, String price, boolean active) {
        MenuItem menuItem = new MenuItem();
        menuItem.setId(id);
        menuItem.setName(name);
        menuItem.setPrice(new BigDecimal(price));
        menuItem.setActive(active);
        return menuItem;
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
