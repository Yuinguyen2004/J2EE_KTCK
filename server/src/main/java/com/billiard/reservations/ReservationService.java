package com.billiard.reservations;

import com.billiard.customers.AuthenticatedCustomerService;
import com.billiard.customers.Customer;
import com.billiard.customers.CustomerRepository;
import com.billiard.reservations.dto.ConfirmReservationRequest;
import com.billiard.reservations.dto.CreateReservationRequest;
import com.billiard.reservations.dto.CustomerReservationRequest;
import com.billiard.reservations.dto.ReservationResponse;
import com.billiard.reservations.dto.UpdateReservationRequest;
import com.billiard.shared.websocket.FloorEvents;
import com.billiard.shared.web.PageRequestFactory;
import com.billiard.shared.web.PageResponse;
import com.billiard.tables.BilliardTable;
import com.billiard.tables.BilliardTableRepository;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN
    );

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "reservedFrom", "reservedFrom",
            "reservedTo", "reservedTo",
            "checkedInAt", "checkedInAt",
            "status", "status",
            "tableName", "table.name",
            "customerName", "customer.user.fullName",
            "staffName", "staff.fullName",
            "updatedAt", "updatedAt"
    );

    private final ReservationRepository reservationRepository;
    private final BilliardTableRepository billiardTableRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final AuthenticatedCustomerService authenticatedCustomerService;
    private final FloorEvents floorEvents;
    private final Clock clock;

    public ReservationService(
            ReservationRepository reservationRepository,
            BilliardTableRepository billiardTableRepository,
            CustomerRepository customerRepository,
            UserRepository userRepository,
            AuthenticatedCustomerService authenticatedCustomerService,
            FloorEvents floorEvents,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.billiardTableRepository = billiardTableRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.authenticatedCustomerService = authenticatedCustomerService;
        this.floorEvents = floorEvents;
        this.clock = clock;
    }

    @Transactional
    public ReservationResponse create(CreateReservationRequest request, String staffEmail) {
        User staff = findStaff(staffEmail);
        BilliardTable table = findTableForUpdate(request.tableId());
        Customer customer = findCustomer(request.customerId());

        validateTimeRange(request.reservedFrom(), request.reservedTo());
        ensureNoOverlap(table.getId(), request.reservedFrom(), request.reservedTo(), null, ReservationStatus.PENDING);

        Reservation reservation = new Reservation();
        reservation.setTable(table);
        reservation.setCustomer(customer);
        reservation.setStaff(staff);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setReservedFrom(request.reservedFrom());
        reservation.setReservedTo(request.reservedTo());
        reservation.setPartySize(request.partySize());
        reservation.setCheckedInAt(null);
        reservation.setNotes(normalizeNullable(request.notes()));

        ReservationResponse response = toResponse(reservationRepository.save(reservation));
        floorEvents.reservationChanged(response);
        return response;
    }

    @Transactional
    public ReservationResponse update(
            Long id,
            UpdateReservationRequest request,
            String staffEmail
    ) {
        Reservation reservation = findEntity(id);
        User staff = findStaff(staffEmail);
        BilliardTable table = findTableForUpdate(request.tableId());
        Customer customer = findCustomer(request.customerId());

        validateTimeRange(request.reservedFrom(), request.reservedTo());
        validateTransition(reservation.getStatus(), request.status());
        ensureNoOverlap(
                table.getId(),
                request.reservedFrom(),
                request.reservedTo(),
                reservation.getId(),
                request.status()
        );

        reservation.setTable(table);
        reservation.setCustomer(customer);
        reservation.setStaff(staff);
        reservation.setStatus(request.status());
        reservation.setReservedFrom(request.reservedFrom());
        reservation.setReservedTo(request.reservedTo());
        reservation.setPartySize(request.partySize());
        reservation.setNotes(normalizeNullable(request.notes()));

        if (request.status() == ReservationStatus.CHECKED_IN) {
            if (reservation.getCheckedInAt() == null) {
                reservation.setCheckedInAt(Instant.now(clock));
            }
        } else {
            reservation.setCheckedInAt(null);
        }

        ReservationResponse response = toResponse(reservationRepository.save(reservation));
        floorEvents.reservationChanged(response);
        return response;
    }

    @Transactional
    public ReservationResponse createCustomerRequest(
            CustomerReservationRequest request,
            String customerEmail
    ) {
        Customer customer = authenticatedCustomerService.getRequiredCustomer(customerEmail);
        validateTimeRange(request.reservedFrom(), request.reservedTo());

        Reservation reservation = new Reservation();
        reservation.setTable(null);
        reservation.setCustomer(customer);
        reservation.setStaff(null);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setReservedFrom(request.reservedFrom());
        reservation.setReservedTo(request.reservedTo());
        reservation.setPartySize(request.partySize());
        reservation.setCheckedInAt(null);
        reservation.setNotes(normalizeNullable(request.notes()));

        ReservationResponse response = toResponse(reservationRepository.save(reservation));
        floorEvents.reservationChanged(response);
        return response;
    }

    @Transactional
    public ReservationResponse updateCustomerRequest(
            Long id,
            CustomerReservationRequest request,
            String customerEmail
    ) {
        Customer customer = authenticatedCustomerService.getRequiredCustomer(customerEmail);
        Reservation reservation = findOwnedEntityForUpdate(id, customer.getId());
        ensureCustomerMutable(reservation);
        validateTimeRange(request.reservedFrom(), request.reservedTo());

        reservation.setReservedFrom(request.reservedFrom());
        reservation.setReservedTo(request.reservedTo());
        reservation.setPartySize(request.partySize());
        reservation.setNotes(normalizeNullable(request.notes()));

        ReservationResponse response = toResponse(reservationRepository.save(reservation));
        floorEvents.reservationChanged(response);
        return response;
    }

    @Transactional
    public void cancelCustomerRequest(Long id, String customerEmail) {
        Customer customer = authenticatedCustomerService.getRequiredCustomer(customerEmail);
        Reservation reservation = findOwnedEntityForUpdate(id, customer.getId());
        ensureCustomerMutable(reservation);

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setStaff(null);
        ReservationResponse response = toResponse(reservationRepository.save(reservation));
        floorEvents.reservationChanged(response);
    }

    @Transactional
    public ReservationResponse confirm(Long id, ConfirmReservationRequest request, String staffEmail) {
        Reservation reservation = findEntityForUpdate(id);
        User staff = findStaff(staffEmail);
        BilliardTable table = findTableForUpdate(request.tableId());

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending reservations can be confirmed"
            );
        }

        validateTimeRange(reservation.getReservedFrom(), reservation.getReservedTo());
        ensureNoOverlap(
                table.getId(),
                reservation.getReservedFrom(),
                reservation.getReservedTo(),
                reservation.getId(),
                ReservationStatus.CONFIRMED
        );

        reservation.setTable(table);
        reservation.setStaff(staff);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        if (StringUtils.hasText(request.notes())) {
            reservation.setNotes(normalizeNullable(request.notes()));
        }

        ReservationResponse response = toResponse(reservationRepository.save(reservation));
        floorEvents.reservationChanged(response);
        return response;
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public ReservationResponse getOwned(Long id, String customerEmail) {
        Customer customer = authenticatedCustomerService.getRequiredCustomer(customerEmail);
        return toResponse(findOwnedEntity(id, customer.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> list(
            Long tableId,
            Long customerId,
            ReservationStatus status,
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
                "reservedFrom",
                SORT_FIELDS
        );
        Page<Reservation> reservations = reservationRepository.findAll(
                buildSpecification(tableId, customerId, status, q),
                pageable
        );
        return PageResponse.from(reservations, this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> listOwned(
            ReservationStatus status,
            String q,
            Integer page,
            Integer size,
            String sortBy,
            String direction,
            String customerEmail
    ) {
        Customer customer = authenticatedCustomerService.getRequiredCustomer(customerEmail);
        return list(null, customer.getId(), status, q, page, size, sortBy, direction);
    }

    private Specification<Reservation> buildSpecification(
            Long tableId,
            Long customerId,
            ReservationStatus status,
            String q
    ) {
        return (root, query, criteriaBuilder) -> {
            if (query != null) {
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();
            if (tableId != null) {
                predicates.add(criteriaBuilder.equal(root.get("table").get("id"), tableId));
            }
            if (customerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("customer").get("id"), customerId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (StringUtils.hasText(q)) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                var tableJoin = root.join("table", JoinType.LEFT);
                var customerJoin = root.join("customer", JoinType.INNER);
                var customerUserJoin = customerJoin.join("user", JoinType.INNER);
                var staffJoin = root.join("staff", JoinType.LEFT);

                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("notes")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(tableJoin.get("name")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(customerUserJoin.get("fullName")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(customerUserJoin.get("email")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(staffJoin.get("fullName")), pattern, '\\')
                ));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private ReservationResponse toResponse(Reservation reservation) {
        BilliardTable table = reservation.getTable();
        Customer customer = reservation.getCustomer();
        User staff = reservation.getStaff();

        return new ReservationResponse(
                reservation.getId(),
                table == null ? null : table.getId(),
                table == null ? null : table.getName(),
                table == null ? null : table.getStatus(),
                customer.getId(),
                customer.getUser().getFullName(),
                staff == null ? null : staff.getId(),
                staff == null ? null : staff.getFullName(),
                reservation.getStatus(),
                reservation.getReservedFrom(),
                reservation.getReservedTo(),
                reservation.getPartySize(),
                reservation.getCheckedInAt(),
                reservation.getNotes(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }

    private Reservation findEntity(Long id) {
        return reservationRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Reservation not found"
        ));
    }

    private Reservation findEntityForUpdate(Long id) {
        return reservationRepository.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Reservation not found"
        ));
    }

    private Reservation findOwnedEntity(Long id, Long customerId) {
        return ensureOwnedReservation(findEntity(id), customerId);
    }

    private Reservation findOwnedEntityForUpdate(Long id, Long customerId) {
        return ensureOwnedReservation(findEntityForUpdate(id), customerId);
    }

    private Reservation ensureOwnedReservation(Reservation reservation, Long customerId) {
        if (!reservation.getCustomer().getId().equals(customerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only manage your own reservations"
            );
        }
        return reservation;
    }

    private BilliardTable findTable(Long tableId) {
        BilliardTable table = billiardTableRepository.findById(tableId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Table not found"
                ));

        if (!table.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inactive tables cannot be reserved"
            );
        }

        return table;
    }

    private BilliardTable findTableForUpdate(Long tableId) {
        BilliardTable table = billiardTableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Table not found"
                ));

        if (!table.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inactive tables cannot be reserved"
            );
        }

        return table;
    }

    private Customer findCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found"
                ));

        if (!customer.getUser().isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inactive customers cannot hold reservations"
            );
        }

        return customer;
    }

    private User findStaff(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.getRole() != UserRole.CUSTOMER)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Only staff can manage reservations"
                ));
    }

    private void validateTimeRange(Instant reservedFrom, Instant reservedTo) {
        if (!reservedFrom.isBefore(reservedTo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "reservedFrom must be earlier than reservedTo"
            );
        }
    }

    private void validateTransition(ReservationStatus currentStatus, ReservationStatus targetStatus) {
        boolean allowed = switch (currentStatus) {
            case PENDING -> targetStatus == ReservationStatus.PENDING
                    || targetStatus == ReservationStatus.CONFIRMED
                    || targetStatus == ReservationStatus.CANCELLED
                    || targetStatus == ReservationStatus.NO_SHOW;
            case CONFIRMED -> targetStatus == ReservationStatus.CONFIRMED
                    || targetStatus == ReservationStatus.CHECKED_IN
                    || targetStatus == ReservationStatus.CANCELLED
                    || targetStatus == ReservationStatus.NO_SHOW;
            case CHECKED_IN -> targetStatus == ReservationStatus.CHECKED_IN;
            case CANCELLED -> targetStatus == ReservationStatus.CANCELLED;
            case NO_SHOW -> targetStatus == ReservationStatus.NO_SHOW;
        };

        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unsupported reservation status transition"
            );
        }
    }

    private void ensureNoOverlap(
            Long tableId,
            Instant reservedFrom,
            Instant reservedTo,
            Long reservationId,
            ReservationStatus targetStatus
    ) {
        if (tableId == null || !ACTIVE_STATUSES.contains(targetStatus)) {
            return;
        }

        boolean overlaps = reservationId == null
                ? reservationRepository.existsByTable_IdAndStatusInAndReservedFromLessThanAndReservedToGreaterThan(
                        tableId,
                        ACTIVE_STATUSES,
                        reservedTo,
                        reservedFrom
                )
                : reservationRepository
                        .existsByTable_IdAndIdNotAndStatusInAndReservedFromLessThanAndReservedToGreaterThan(
                                tableId,
                                reservationId,
                                ACTIVE_STATUSES,
                                reservedTo,
                                reservedFrom
                        );

        if (overlaps) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The selected table already has an overlapping reservation"
            );
        }
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

    private void ensureCustomerMutable(Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending reservations can be edited or cancelled"
            );
        }
    }
}
