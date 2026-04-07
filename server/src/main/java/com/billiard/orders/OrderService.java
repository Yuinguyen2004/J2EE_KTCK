package com.billiard.orders;

import com.billiard.billing.SessionStatus;
import com.billiard.billing.TableSession;
import com.billiard.billing.TableSessionRepository;
import com.billiard.customers.AuthenticatedCustomerService;
import com.billiard.customers.Customer;
import com.billiard.orders.dto.CreateOrderItemRequest;
import com.billiard.orders.dto.CreateOrderRequest;
import com.billiard.orders.dto.OrderItemResponse;
import com.billiard.orders.dto.OrderResponse;
import com.billiard.orders.dto.UpdateOrderRequest;
import com.billiard.shared.web.PageRequestFactory;
import com.billiard.shared.web.PageResponse;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

    private static final List<SessionStatus> ACTIVE_SESSION_STATUSES = List.of(
            SessionStatus.ACTIVE,
            SessionStatus.PAUSED
    );

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "orderedAt", "orderedAt",
            "totalAmount", "totalAmount",
            "updatedAt", "updatedAt"
    );

    private static final Comparator<OrderItem> ORDER_ITEM_COMPARATOR = Comparator
            .comparing((OrderItem item) -> item.getOrder().getId())
            .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId());

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final TableSessionRepository tableSessionRepository;
    private final UserRepository userRepository;
    private final AuthenticatedCustomerService authenticatedCustomerService;
    private final Clock clock;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            MenuItemRepository menuItemRepository,
            TableSessionRepository tableSessionRepository,
            UserRepository userRepository,
            AuthenticatedCustomerService authenticatedCustomerService,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.tableSessionRepository = tableSessionRepository;
        this.userRepository = userRepository;
        this.authenticatedCustomerService = authenticatedCustomerService;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, String staffEmail) {
        List<CreateOrderItemRequest> requestedItems = validateItems(request.items());
        User staff = findStaff(staffEmail);
        TableSession session = findActiveSession(request.sessionId());
        Map<Long, MenuItem> menuItemsById = loadMenuItems(requestedItems);

        return createOrder(
                session,
                session.getCustomer(),
                staff,
                OrderStatus.CONFIRMED,
                requestedItems,
                menuItemsById,
                request.notes()
        );
    }

    @Transactional
    public OrderResponse createCustomerDraft(CreateOrderRequest request, String customerEmail) {
        List<CreateOrderItemRequest> requestedItems = validateItems(request.items());
        Customer customer = authenticatedCustomerService.getRequiredCustomer(customerEmail);
        TableSession session = findOwnedActiveSession(request.sessionId(), customer.getId());
        Map<Long, MenuItem> menuItemsById = loadMenuItems(requestedItems);

        return createOrder(
                session,
                customer,
                null,
                OrderStatus.PENDING,
                requestedItems,
                menuItemsById,
                request.notes()
        );
    }

    @Transactional
    public OrderResponse updateCustomerDraft(Long id, UpdateOrderRequest request, String customerEmail) {
        List<CreateOrderItemRequest> requestedItems = validateItems(request.items());
        Customer customer = authenticatedCustomerService.getRequiredCustomer(customerEmail);
        Order order = findOwnedOrderForUpdate(id, customer.getId());
        ensureCustomerDraftMutable(order);
        Map<Long, MenuItem> menuItemsById = loadMenuItems(requestedItems);

        orderItemRepository.deleteAllByOrder_Id(order.getId());
        List<OrderItem> savedItems = orderItemRepository.saveAll(buildOrderItems(order, requestedItems, menuItemsById));
        BigDecimal totalAmount = savedItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setTotalAmount(totalAmount);
        order.setNotes(normalizeNullable(request.notes()));
        Order persistedOrder = orderRepository.save(order);
        return toResponse(persistedOrder, sortOrderItems(savedItems));
    }

    @Transactional
    public void deleteCustomerDraft(Long id, String customerEmail) {
        Customer customer = authenticatedCustomerService.getRequiredCustomer(customerEmail);
        Order order = findOwnedOrderForUpdate(id, customer.getId());
        ensureCustomerDraftMutable(order);
        orderItemRepository.deleteAllByOrder_Id(order.getId());
        orderRepository.delete(order);
    }

    @Transactional
    public OrderResponse confirm(Long id, String staffEmail) {
        User staff = findStaff(staffEmail);
        Order order = findOrderForUpdate(id);

        if (resolveStatus(order) != OrderStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending orders can be confirmed"
            );
        }

        order.setStatus(OrderStatus.CONFIRMED);
        order.setStaff(staff);
        return toResponse(orderRepository.save(order));
    }

    private OrderResponse createOrder(
            TableSession session,
            Customer customer,
            User staff,
            OrderStatus status,
            List<CreateOrderItemRequest> requestedItems,
            Map<Long, MenuItem> menuItemsById,
            String notes
    ) {
        Order order = new Order();
        order.setSession(session);
        order.setCustomer(customer);
        order.setStaff(staff);
        order.setStatus(status);
        order.setOrderedAt(Instant.now(clock));
        order.setNotes(normalizeNullable(notes));
        order.setTotalAmount(BigDecimal.ZERO);

        Order savedOrder = orderRepository.save(order);
        List<OrderItem> orderItems = buildOrderItems(savedOrder, requestedItems, menuItemsById);
        List<OrderItem> savedItems = orderItemRepository.saveAll(orderItems);
        BigDecimal totalAmount = savedItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        savedOrder.setTotalAmount(totalAmount);
        Order persistedOrder = orderRepository.save(savedOrder);
        return toResponse(persistedOrder, sortOrderItems(savedItems));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        Order order = findOrder(id);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOwned(Long id, String customerEmail) {
        Customer customer = authenticatedCustomerService.getRequiredCustomer(customerEmail);
        Order order = findOwnedOrder(id, customer.getId());
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(
            Long sessionId,
            String q,
            Integer page,
            Integer size,
            String sortBy,
            String direction
    ) {
        return listInternal(sessionId, q, page, size, sortBy, direction, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listOwned(
            Long sessionId,
            String q,
            Integer page,
            Integer size,
            String sortBy,
            String direction,
            String customerEmail
    ) {
        Customer customer = authenticatedCustomerService.getRequiredCustomer(customerEmail);
        return listInternal(sessionId, q, page, size, sortBy, direction, customer.getId());
    }

    private PageResponse<OrderResponse> listInternal(
            Long sessionId,
            String q,
            Integer page,
            Integer size,
            String sortBy,
            String direction,
            Long customerId
    ) {
        Pageable pageable = PageRequestFactory.create(
                page,
                size,
                sortBy,
                direction,
                "orderedAt",
                SORT_FIELDS
        );
        Page<Order> orders = orderRepository.findAll(buildSpecification(sessionId, q, customerId), pageable);
        if (orders.isEmpty()) {
            return PageResponse.from(orders, ignored -> null);
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrderId = orderItemRepository.findAllByOrder_IdIn(orderIds)
                .stream()
                .sorted(ORDER_ITEM_COMPARATOR)
                .collect(Collectors.groupingBy(
                        item -> item.getOrder().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return new PageResponse<>(
                orders.getContent().stream()
                        .map(order -> toResponse(
                                order,
                                itemsByOrderId.getOrDefault(order.getId(), List.of())
                        ))
                        .toList(),
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages()
        );
    }

    private List<CreateOrderItemRequest> validateItems(List<CreateOrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one order item is required"
            );
        }

        Set<Long> seenMenuItemIds = new HashSet<>();
        for (CreateOrderItemRequest item : items) {
            if (!seenMenuItemIds.add(item.menuItemId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate menu items are not allowed in one order"
                );
            }
        }

        return items;
    }

    private Map<Long, MenuItem> loadMenuItems(Collection<CreateOrderItemRequest> requestedItems) {
        List<Long> menuItemIds = requestedItems.stream()
                .map(CreateOrderItemRequest::menuItemId)
                .toList();
        Map<Long, MenuItem> menuItemsById = menuItemRepository.findAllById(menuItemIds)
                .stream()
                .collect(Collectors.toMap(MenuItem::getId, menuItem -> menuItem));

        if (menuItemsById.size() != menuItemIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more menu items were not found");
        }

        menuItemsById.values().stream()
                .filter(menuItem -> !menuItem.isActive())
                .findFirst()
                .ifPresent(menuItem -> {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Inactive menu items cannot be ordered"
                    );
                });

        return menuItemsById;
    }

    private List<OrderItem> buildOrderItems(
            Order order,
            List<CreateOrderItemRequest> requestedItems,
            Map<Long, MenuItem> menuItemsById
    ) {
        List<OrderItem> orderItems = new ArrayList<>();
        for (CreateOrderItemRequest requestItem : requestedItems) {
            MenuItem menuItem = menuItemsById.get(requestItem.menuItemId());
            BigDecimal unitPrice = menuItem.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(requestItem.quantity().longValue()));

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setMenuItem(menuItem);
            orderItem.setQuantity(requestItem.quantity());
            orderItem.setUnitPrice(unitPrice);
            orderItem.setSubtotal(subtotal);
            orderItems.add(orderItem);
        }
        return orderItems;
    }

    private Specification<Order> buildSpecification(Long sessionId, String q, Long customerId) {
        return (root, query, criteriaBuilder) -> {
            if (query != null) {
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();
            if (sessionId != null) {
                predicates.add(criteriaBuilder.equal(root.get("session").get("id"), sessionId));
            }
            if (customerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("customer").get("id"), customerId));
            }

            if (StringUtils.hasText(q)) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                var sessionJoin = root.join("session", JoinType.INNER);
                var tableJoin = sessionJoin.join("table", JoinType.INNER);
                var staffJoin = root.join("staff", JoinType.LEFT);
                var customerJoin = root.join("customer", JoinType.LEFT);
                var customerUserJoin = customerJoin.join("user", JoinType.LEFT);

                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("notes")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(tableJoin.get("name")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(staffJoin.get("fullName")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(customerUserJoin.get("fullName")), pattern, '\\')
                ));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private OrderResponse toResponse(Order order) {
        return toResponse(order, loadItems(order.getId()));
    }

    private OrderResponse toResponse(Order order, List<OrderItem> items) {
        TableSession session = order.getSession();
        Customer customer = order.getCustomer();
        User staff = order.getStaff();

        return new OrderResponse(
                order.getId(),
                session.getId(),
                session.getStatus(),
                session.getTable().getId(),
                session.getTable().getName(),
                customer == null ? null : customer.getId(),
                customer == null ? null : customer.getUser().getFullName(),
                staff == null ? null : staff.getId(),
                staff == null ? null : staff.getFullName(),
                resolveStatus(order),
                order.getTotalAmount(),
                order.getOrderedAt(),
                order.getNotes(),
                sortOrderItems(items).stream().map(this::toItemResponse).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getMenuItem().getId(),
                item.getMenuItem().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
        );
    }

    private List<OrderItem> loadItems(Long orderId) {
        return sortOrderItems(orderItemRepository.findAllByOrder_Id(orderId));
    }

    private List<OrderItem> sortOrderItems(List<OrderItem> items) {
        return items.stream().sorted(ORDER_ITEM_COMPARATOR).toList();
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Order not found"
        ));
    }

    private Order findOwnedOrder(Long id, Long customerId) {
        Order order = findOrder(id);
        return ensureOwnedOrder(order, customerId);
    }

    private Order findOrderForUpdate(Long id) {
        return orderRepository.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Order not found"
        ));
    }

    private TableSession findActiveSession(Long sessionId) {
        TableSession session = tableSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found"
                ));

        if (!ACTIVE_SESSION_STATUSES.contains(session.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Orders can only be created for active sessions"
            );
        }

        return session;
    }

    private TableSession findOwnedActiveSession(Long sessionId, Long customerId) {
        TableSession session = findActiveSession(sessionId);
        if (session.getCustomer() == null || !session.getCustomer().getId().equals(customerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only manage orders for your own active session"
            );
        }
        return session;
    }

    private Order findOwnedOrderForUpdate(Long id, Long customerId) {
        Order order = findOrderForUpdate(id);
        return ensureOwnedOrder(order, customerId);
    }

    private Order ensureOwnedOrder(Order order, Long customerId) {
        if (order.getCustomer() == null || !order.getCustomer().getId().equals(customerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only manage your own orders"
            );
        }
        return order;
    }

    private void ensureCustomerDraftMutable(Order order) {
        if (resolveStatus(order) != OrderStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending orders can be edited"
            );
        }

        if (!ACTIVE_SESSION_STATUSES.contains(order.getSession().getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Orders can only be edited while the session is active"
            );
        }
    }

    private User findStaff(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.getRole() != UserRole.CUSTOMER)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Only staff can manage orders"
                ));
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private OrderStatus resolveStatus(Order order) {
        return order.getStatus() == null ? OrderStatus.CONFIRMED : order.getStatus();
    }
}
