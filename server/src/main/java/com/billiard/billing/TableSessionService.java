package com.billiard.billing;

import com.billiard.billing.dto.EndSessionResponse;
import com.billiard.billing.dto.PauseSessionRequest;
import com.billiard.billing.dto.SessionPauseResponse;
import com.billiard.billing.dto.StartSessionRequest;
import com.billiard.billing.dto.InvoiceResponse;
import com.billiard.billing.dto.TableSessionResponse;
import com.billiard.customers.Customer;
import com.billiard.memberships.MembershipTier;
import com.billiard.customers.CustomerRepository;
import com.billiard.reservations.ReservationRepository;
import com.billiard.reservations.ReservationStatus;
import com.billiard.shared.websocket.FloorEvents;
import com.billiard.tables.BilliardTable;
import com.billiard.tables.BilliardTableRepository;
import com.billiard.tables.TableStatus;
import com.billiard.tables.dto.BilliardTableResponse;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TableSessionService {

    private static final List<SessionStatus> ACTIVE_STATUSES = List.of(
            SessionStatus.ACTIVE,
            SessionStatus.PAUSED
    );

    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES = List.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN
    );

    private final TableSessionRepository tableSessionRepository;
    private final SessionPauseRepository sessionPauseRepository;
    private final BilliardTableRepository billiardTableRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final InvoiceService invoiceService;
    private final FloorEvents floorEvents;
    private final Clock clock;

    public TableSessionService(
            TableSessionRepository tableSessionRepository,
            SessionPauseRepository sessionPauseRepository,
            BilliardTableRepository billiardTableRepository,
            CustomerRepository customerRepository,
            UserRepository userRepository,
            ReservationRepository reservationRepository,
            InvoiceService invoiceService,
            FloorEvents floorEvents,
            Clock clock
    ) {
        this.tableSessionRepository = tableSessionRepository;
        this.sessionPauseRepository = sessionPauseRepository;
        this.billiardTableRepository = billiardTableRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.invoiceService = invoiceService;
        this.floorEvents = floorEvents;
        this.clock = clock;
    }

    @Transactional
    public TableSessionResponse startSession(
            Long tableId,
            StartSessionRequest request,
            String staffEmail
    ) {
        BilliardTable table = findTableForUpdate(tableId);
        ensureNoActiveSession(tableId);

        boolean overrideReserved = request.overrideReserved() != null && request.overrideReserved();
        if (table.getStatus() == TableStatus.RESERVED && !overrideReserved) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reserved tables require overrideReserved=true"
            );
        }

        if (table.getStatus() != TableStatus.AVAILABLE && table.getStatus() != TableStatus.RESERVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Table is not available for a new session"
            );
        }

        Instant now = Instant.now(clock);
        if (!overrideReserved && hasActiveReservation(tableId, now)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Table has an active reservation and requires override"
            );
        }

        TableSession session = new TableSession();
        session.setTable(table);
        session.setCustomer(findCustomer(request.customerId()));
        session.setStaff(findStaff(staffEmail));
        session.setStatus(SessionStatus.ACTIVE);
        session.setStartedAt(now);
        session.setNotes(normalizeNullable(request.notes()));

        table.setStatus(TableStatus.IN_USE);
        billiardTableRepository.save(table);

        TableSession savedSession = tableSessionRepository.save(session);
        TableSessionResponse response = toResponse(savedSession);
        publishFloorUpdates(savedSession.getTable(), response);
        return response;
    }

    @Transactional
    public TableSessionResponse pauseSession(
            Long sessionId,
            PauseSessionRequest request,
            String staffEmail
    ) {
        TableSession session = findActiveSession(sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only active sessions can be paused"
            );
        }

        findStaff(staffEmail);

        SessionPause pause = new SessionPause();
        pause.setSession(session);
        pause.setStartedAt(Instant.now(clock));
        pause.setReason(normalizeNullable(request.reason()));
        sessionPauseRepository.save(pause);

        session.setStatus(SessionStatus.PAUSED);
        session.getTable().setStatus(TableStatus.PAUSED);
        billiardTableRepository.save(session.getTable());
        TableSession savedSession = tableSessionRepository.save(session);
        TableSessionResponse response = toResponse(savedSession);
        publishFloorUpdates(savedSession.getTable(), response);
        return response;
    }

    @Transactional
    public TableSessionResponse resumeSession(Long sessionId, String staffEmail) {
        TableSession session = findActiveSession(sessionId);
        if (session.getStatus() != SessionStatus.PAUSED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only paused sessions can be resumed"
            );
        }

        findStaff(staffEmail);
        closeActivePause(session);

        session.setStatus(SessionStatus.ACTIVE);
        session.getTable().setStatus(TableStatus.IN_USE);
        billiardTableRepository.save(session.getTable());
        TableSession savedSession = tableSessionRepository.save(session);
        TableSessionResponse response = toResponse(savedSession);
        publishFloorUpdates(savedSession.getTable(), response);
        return response;
    }

    @Transactional
    public EndSessionResponse endSession(Long sessionId, String staffEmail) {
        TableSession session = findActiveSession(sessionId);
        findStaff(staffEmail);

        if (session.getStatus() == SessionStatus.PAUSED) {
            closeActivePause(session);
        }

        session.setStatus(SessionStatus.COMPLETED);
        session.setEndedAt(Instant.now(clock));
        session.getTable().setStatus(TableStatus.AVAILABLE);
        billiardTableRepository.save(session.getTable());
        TableSession savedSession = tableSessionRepository.save(session);
        InvoiceResponse invoiceResponse = invoiceService.generateForSession(savedSession.getId());
        invoiceResponse = invoiceService.issue(invoiceResponse.id(), staffEmail);
        invoiceResponse = invoiceService.pay(invoiceResponse.id(), staffEmail);
        TableSessionResponse sessionResponse = toResponse(savedSession);
        publishFloorUpdates(savedSession.getTable(), sessionResponse);
        return new EndSessionResponse(sessionResponse, invoiceResponse);
    }

    @Transactional(readOnly = true)
    public TableSessionResponse getSession(Long sessionId) {
        TableSession session = tableSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found"
                ));
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public TableSessionResponse getActiveSessionForTable(Long tableId) {
        findTable(tableId);

        TableSession session = tableSessionRepository.findFirstByTable_IdAndStatusInOrderByStartedAtDesc(
                tableId,
                ACTIVE_STATUSES
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Active session not found"
        ));

        return toResponse(session);
    }

    private void ensureNoActiveSession(Long tableId) {
        tableSessionRepository.findFirstByTable_IdAndStatusInOrderByStartedAtDesc(
                tableId,
                ACTIVE_STATUSES
        ).ifPresent(existing -> {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The table already has an active session"
            );
        });
    }

    private boolean hasActiveReservation(Long tableId, Instant now) {
        return reservationRepository.existsByTable_IdAndStatusInAndReservedFromLessThanAndReservedToGreaterThan(
                tableId,
                ACTIVE_RESERVATION_STATUSES,
                now,
                now
        );
    }

    private TableSession findActiveSession(Long sessionId) {
        TableSession session = tableSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found"
                ));

        if (!ACTIVE_STATUSES.contains(session.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Session is no longer active"
            );
        }

        return session;
    }

    private void closeActivePause(TableSession session) {
        SessionPause pause = sessionPauseRepository
                .findFirstBySession_IdAndEndedAtIsNullOrderByStartedAtDesc(session.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Session does not have an open pause"
                ));

        Instant now = Instant.now(clock);
        pause.setEndedAt(now);
        long durationSeconds = Duration.between(pause.getStartedAt(), now).getSeconds();
        session.setTotalPausedSeconds(session.getTotalPausedSeconds() + Math.max(durationSeconds, 0L));
        sessionPauseRepository.save(pause);
    }

    private TableSessionResponse toResponse(TableSession session) {
        List<SessionPause> pauses = sessionPauseRepository.findAllBySession_IdOrderByStartedAtAsc(
                session.getId()
        );
        long elapsedSeconds = Duration.between(
                session.getStartedAt(),
                session.getEndedAt() == null ? Instant.now(clock) : session.getEndedAt()
        ).getSeconds();
        long openPauseSeconds = pauses.stream()
                .filter(pause -> pause.getEndedAt() == null)
                .findFirst()
                .map(pause -> Duration.between(pause.getStartedAt(), Instant.now(clock)).getSeconds())
                .orElse(0L);
        long billableSeconds = Math.max(
                elapsedSeconds - session.getTotalPausedSeconds() - openPauseSeconds,
                0L
        );

        User staff = session.getStaff();
        Customer customer = session.getCustomer();
        MembershipTier membershipTier = customer == null ? null : customer.getMembershipTier();
        BilliardTable table = session.getTable();

        return new TableSessionResponse(
                session.getId(),
                table.getId(),
                table.getName(),
                table.getStatus(),
                customer == null ? null : customer.getId(),
                customer == null ? null : customer.getUser().getFullName(),
                membershipTier == null ? null : membershipTier.getName(),
                membershipTier == null ? null : membershipTier.getDiscountPercent(),
                staff == null ? null : staff.getId(),
                staff == null ? null : staff.getFullName(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                Math.max(elapsedSeconds, 0L),
                billableSeconds,
                session.getTotalPausedSeconds(),
                session.getTotalAmount(),
                session.getNotes(),
                pauses.stream().map(this::toPauseResponse).toList()
        );
    }

    private void publishFloorUpdates(BilliardTable table, TableSessionResponse sessionResponse) {
        floorEvents.tableStatusChanged(toTableResponse(table));
        floorEvents.sessionChanged(sessionResponse);
    }

    private BilliardTableResponse toTableResponse(BilliardTable table) {
        return new BilliardTableResponse(
                table.getId(),
                table.getName(),
                table.getTableType().getId(),
                table.getTableType().getName(),
                table.getStatus(),
                table.getFloorPositionX(),
                table.getFloorPositionY(),
                table.isActive(),
                table.getCreatedAt(),
                table.getUpdatedAt()
        );
    }

    private SessionPauseResponse toPauseResponse(SessionPause pause) {
        Instant endedAt = pause.getEndedAt();
        long durationSeconds = Duration.between(
                pause.getStartedAt(),
                endedAt == null ? Instant.now(clock) : endedAt
        ).getSeconds();
        return new SessionPauseResponse(
                pause.getId(),
                pause.getStartedAt(),
                endedAt,
                pause.getReason(),
                Math.max(durationSeconds, 0L)
        );
    }

    private BilliardTable findTable(Long tableId) {
        return billiardTableRepository.findById(tableId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Table not found"
                ));
    }

    private BilliardTable findTableForUpdate(Long tableId) {
        return billiardTableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Table not found"
                ));
    }

    private Customer findCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found"
                ));

        if (!customer.getUser().isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inactive customers cannot start a session"
            );
        }

        return customer;
    }

    private User findStaff(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.getRole() != UserRole.CUSTOMER)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Only staff can manage sessions"
                ));
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
