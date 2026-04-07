package com.billiard.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.billiard.billing.dto.InvoiceResponse;
import com.billiard.billing.dto.PauseSessionRequest;
import com.billiard.billing.dto.StartSessionRequest;
import com.billiard.customers.Customer;
import com.billiard.customers.CustomerRepository;
import com.billiard.memberships.MembershipTier;
import com.billiard.reservations.ReservationRepository;
import com.billiard.shared.websocket.FloorEvents;
import com.billiard.tables.BilliardTable;
import com.billiard.tables.BilliardTableRepository;
import com.billiard.tables.TableStatus;
import com.billiard.tables.TableType;
import com.billiard.tables.dto.BilliardTableResponse;
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
class TableSessionServiceTest {

    @Mock
    private TableSessionRepository tableSessionRepository;

    @Mock
    private SessionPauseRepository sessionPauseRepository;

    @Mock
    private BilliardTableRepository billiardTableRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private FloorEvents floorEvents;

    private MutableClock clock;
    private TableSessionService tableSessionService;

    private BilliardTable table;
    private User staff;
    private Customer customer;
    private TableSession storedSession;
    private final List<SessionPause> storedPauses = new ArrayList<>();
    private final AtomicLong sessionIdSequence = new AtomicLong(100L);
    private final AtomicLong pauseIdSequence = new AtomicLong(200L);

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-03-28T02:00:00Z"), ZoneId.of("UTC"));
        tableSessionService = new TableSessionService(
                tableSessionRepository,
                sessionPauseRepository,
                billiardTableRepository,
                customerRepository,
                userRepository,
                reservationRepository,
                invoiceService,
                floorEvents,
                clock
        );

        table = buildTable(10L, "Table 10", TableStatus.AVAILABLE);
        staff = buildStaff(15L, "staff@example.com", "Floor Staff", UserRole.STAFF);
        customer = buildCustomer(20L, "customer@example.com", "Customer One");

        lenient().when(billiardTableRepository.findById(table.getId())).thenReturn(Optional.of(table));
        lenient().when(billiardTableRepository.findByIdForUpdate(table.getId())).thenReturn(Optional.of(table));
        lenient().when(invoiceService.generateForSession(anyLong())).thenAnswer(invocation ->
                buildInvoiceResponse(invocation.getArgument(0), "150000.00", InvoiceStatus.DRAFT)
        );
        lenient().when(invoiceService.issue(anyLong(), any())).thenAnswer(invocation ->
                buildInvoiceResponse(storedSession != null ? storedSession.getId() : invocation.getArgument(0), "150000.00", InvoiceStatus.ISSUED)
        );
        lenient().when(invoiceService.pay(anyLong(), any())).thenAnswer(invocation ->
                buildInvoiceResponse(storedSession != null ? storedSession.getId() : invocation.getArgument(0), "150000.00", InvoiceStatus.PAID)
        );
        lenient().when(tableSessionRepository.findFirstByTable_IdAndStatusInOrderByStartedAtDesc(
                eq(table.getId()),
                any()
        )).thenAnswer(invocation -> Optional.ofNullable(storedSession)
                .filter(session -> session.getTable().getId().equals(table.getId()))
                .filter(session -> session.getStatus() == SessionStatus.ACTIVE
                        || session.getStatus() == SessionStatus.PAUSED));
    }

    @Test
    void startSessionTransitionsTableToInUse() {
        stubStartSessionPrerequisites();
        stubStartSessionPersistence();
        StartSessionRequest request = new StartSessionRequest(customer.getId(), false, "  first rack  ");

        var response = tableSessionService.startSession(table.getId(), request, staff.getEmail());

        assertThat(response.status()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(response.tableStatus()).isEqualTo(TableStatus.IN_USE);
        assertThat(response.customerId()).isEqualTo(customer.getId());
        assertThat(response.customerMembershipTierName()).isEqualTo("Silver");
        assertThat(response.customerMembershipDiscountPercent()).isEqualByComparingTo("10");
        assertThat(response.staffId()).isEqualTo(staff.getId());
        assertThat(response.notes()).isEqualTo("first rack");
        assertThat(storedSession.getTable().getStatus()).isEqualTo(TableStatus.IN_USE);
        assertThat(storedSession.getStartedAt()).isEqualTo(clock.instant());
        verify(floorEvents).tableStatusChanged(org.mockito.ArgumentMatchers.argThat(
                (BilliardTableResponse tableResponse) -> tableResponse != null
                        && tableResponse.id().equals(table.getId())
                        && tableResponse.status() == TableStatus.IN_USE
        ));
        verify(floorEvents).sessionChanged(org.mockito.ArgumentMatchers.argThat(
                sessionResponse -> sessionResponse != null
                        && sessionResponse.id().equals(response.id())
                        && sessionResponse.status() == SessionStatus.ACTIVE
                        && sessionResponse.tableStatus() == TableStatus.IN_USE
        ));
    }

    @Test
    void startSessionRejectsReservedTableWithoutOverride() {
        table.setStatus(TableStatus.RESERVED);

        assertThatThrownBy(() -> tableSessionService.startSession(
                table.getId(),
                new StartSessionRequest(customer.getId(), false, null),
                staff.getEmail()
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void startSessionRejectsWhenActiveReservationExistsWithoutOverride() {
        when(reservationRepository.existsByTable_IdAndStatusInAndReservedFromLessThanAndReservedToGreaterThan(
                anyLong(),
                any(),
                any(),
                any()
        )).thenReturn(true);

        assertThatThrownBy(() -> tableSessionService.startSession(
                table.getId(),
                new StartSessionRequest(customer.getId(), false, null),
                staff.getEmail()
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getActiveSessionForTableReturnsLatestLiveSession() {
        stubStartSessionPrerequisites();
        stubStartSessionPersistence();
        tableSessionService.startSession(
                table.getId(),
                new StartSessionRequest(customer.getId(), false, null),
                staff.getEmail()
        );

        clock.advance(Duration.ofMinutes(5));
        var response = tableSessionService.getActiveSessionForTable(table.getId());

        assertThat(response.id()).isEqualTo(storedSession.getId());
        assertThat(response.tableId()).isEqualTo(table.getId());
        assertThat(response.status()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(response.elapsedSeconds()).isEqualTo(Duration.ofMinutes(5).getSeconds());
    }

    @Test
    void getActiveSessionForTableRejectsWhenNoLiveSessionExists() {
        assertThatThrownBy(() -> tableSessionService.getActiveSessionForTable(table.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void pauseResumeAndEndTrackPausedTimeAndResetTableStatus() {
        stubStartSessionPrerequisites();
        stubSessionLifecyclePersistence();
        tableSessionService.startSession(
                table.getId(),
                new StartSessionRequest(customer.getId(), false, null),
                staff.getEmail()
        );

        clock.advance(Duration.ofMinutes(20));
        var paused = tableSessionService.pauseSession(
                storedSession.getId(),
                new PauseSessionRequest("break"),
                staff.getEmail()
        );
        assertThat(paused.status()).isEqualTo(SessionStatus.PAUSED);
        assertThat(paused.tableStatus()).isEqualTo(TableStatus.PAUSED);
        assertThat(paused.pauses()).hasSize(1);

        clock.advance(Duration.ofMinutes(10));
        var resumed = tableSessionService.resumeSession(storedSession.getId(), staff.getEmail());
        assertThat(resumed.status()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(resumed.tableStatus()).isEqualTo(TableStatus.IN_USE);
        assertThat(resumed.totalPausedSeconds()).isEqualTo(Duration.ofMinutes(10).getSeconds());

        clock.advance(Duration.ofMinutes(30));
        var ended = tableSessionService.endSession(storedSession.getId(), staff.getEmail());

        assertThat(ended.session().status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(ended.session().tableStatus()).isEqualTo(TableStatus.AVAILABLE);
        assertThat(ended.session().elapsedSeconds()).isEqualTo(Duration.ofMinutes(60).getSeconds());
        assertThat(ended.session().billableSeconds()).isEqualTo(Duration.ofMinutes(50).getSeconds());
        assertThat(ended.session().totalPausedSeconds()).isEqualTo(Duration.ofMinutes(10).getSeconds());
        assertThat(ended.session().endedAt()).isEqualTo(clock.instant());
        assertThat(ended.invoice().sessionId()).isEqualTo(storedSession.getId());
        assertThat(ended.invoice().totalAmount()).isEqualByComparingTo("150000.00");
        assertThat(ended.invoice().status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(storedPauses.getFirst().getEndedAt()).isEqualTo(Instant.parse("2026-03-28T02:30:00Z"));
        verify(invoiceService).generateForSession(storedSession.getId());
        verify(invoiceService).issue(ended.invoice().id(), staff.getEmail());
        verify(invoiceService).pay(ended.invoice().id(), staff.getEmail());
        verify(floorEvents, org.mockito.Mockito.times(4)).tableStatusChanged(any(BilliardTableResponse.class));
        verify(floorEvents, org.mockito.Mockito.times(4)).sessionChanged(any());
    }

    @Test
    void endSessionClosesOpenPauseBeforeCompleting() {
        stubStartSessionPrerequisites();
        stubSessionLifecyclePersistence();
        tableSessionService.startSession(
                table.getId(),
                new StartSessionRequest(customer.getId(), false, null),
                staff.getEmail()
        );
        clock.advance(Duration.ofMinutes(5));
        tableSessionService.pauseSession(
                storedSession.getId(),
                new PauseSessionRequest("incoming call"),
                staff.getEmail()
        );

        clock.advance(Duration.ofMinutes(15));
        var ended = tableSessionService.endSession(storedSession.getId(), staff.getEmail());

        assertThat(ended.session().status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(ended.session().totalPausedSeconds()).isEqualTo(Duration.ofMinutes(15).getSeconds());
        assertThat(ended.session().billableSeconds()).isEqualTo(Duration.ofMinutes(5).getSeconds());
        verify(invoiceService).generateForSession(storedSession.getId());
        verify(invoiceService).issue(ended.invoice().id(), staff.getEmail());
        verify(invoiceService).pay(ended.invoice().id(), staff.getEmail());
        verify(sessionPauseRepository).findFirstBySession_IdAndEndedAtIsNullOrderByStartedAtDesc(
                storedSession.getId()
        );
        verify(floorEvents, org.mockito.Mockito.times(3)).tableStatusChanged(any(BilliardTableResponse.class));
        verify(floorEvents, org.mockito.Mockito.times(3)).sessionChanged(any());
    }

    private void stubStartSessionPrerequisites() {
        when(userRepository.findByEmailIgnoreCase(staff.getEmail())).thenReturn(Optional.of(staff));
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(reservationRepository.existsByTable_IdAndStatusInAndReservedFromLessThanAndReservedToGreaterThan(
                anyLong(),
                any(),
                any(),
                any()
        )).thenReturn(false);
    }

    private void stubStartSessionPersistence() {
        when(billiardTableRepository.save(any(BilliardTable.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tableSessionRepository.save(any(TableSession.class))).thenAnswer(invocation -> {
            TableSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(sessionIdSequence.getAndIncrement());
            }
            storedSession = session;
            return session;
        });
        when(sessionPauseRepository.findAllBySession_IdOrderByStartedAtAsc(anyLong())).thenAnswer(invocation -> {
            Long sessionId = invocation.getArgument(0);
            return storedPauses.stream()
                    .filter(pause -> pause.getSession().getId().equals(sessionId))
                    .sorted((left, right) -> left.getStartedAt().compareTo(right.getStartedAt()))
                    .toList();
        });
    }

    private void stubSessionLifecyclePersistence() {
        stubStartSessionPersistence();
        lenient().when(tableSessionRepository.findByIdForUpdate(anyLong())).thenAnswer(invocation -> {
            Long requestedId = invocation.getArgument(0);
            if (storedSession != null && requestedId.equals(storedSession.getId())) {
                return Optional.of(storedSession);
            }
            return Optional.empty();
        });
        lenient().when(tableSessionRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long requestedId = invocation.getArgument(0);
            if (storedSession != null && requestedId.equals(storedSession.getId())) {
                return Optional.of(storedSession);
            }
            return Optional.empty();
        });
        when(sessionPauseRepository.save(any(SessionPause.class))).thenAnswer(invocation -> {
            SessionPause pause = invocation.getArgument(0);
            if (pause.getId() == null) {
                pause.setId(pauseIdSequence.getAndIncrement());
                storedPauses.add(pause);
            }
            return pause;
        });
        when(sessionPauseRepository.findFirstBySession_IdAndEndedAtIsNullOrderByStartedAtDesc(anyLong()))
                .thenAnswer(invocation -> {
                    Long sessionId = invocation.getArgument(0);
                    return storedPauses.stream()
                            .filter(pause -> pause.getSession().getId().equals(sessionId))
                            .filter(pause -> pause.getEndedAt() == null)
                            .reduce((left, right) -> right);
                });
    }

    private static BilliardTable buildTable(Long id, String name, TableStatus status) {
        TableType tableType = new TableType();
        tableType.setId(1L);
        tableType.setName("Pool");
        tableType.setActive(true);

        BilliardTable table = new BilliardTable();
        table.setId(id);
        table.setName(name);
        table.setTableType(tableType);
        table.setStatus(status);
        table.setActive(true);
        return table;
    }

    private static User buildStaff(Long id, String email, String fullName, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    private static Customer buildCustomer(Long id, String email, String fullName) {
        User user = buildStaff(30L, email, fullName, UserRole.CUSTOMER);
        MembershipTier membershipTier = new MembershipTier();
        membershipTier.setId(40L);
        membershipTier.setName("Silver");
        membershipTier.setDiscountPercent(new BigDecimal("10"));
        membershipTier.setActive(true);

        Customer customer = new Customer();
        customer.setId(id);
        customer.setUser(user);
        customer.setMembershipTier(membershipTier);
        return customer;
    }

    private static InvoiceResponse buildInvoiceResponse(
            Long sessionId,
            String totalAmount,
            InvoiceStatus status
    ) {
        return new InvoiceResponse(
                500L,
                sessionId,
                10L,
                "Table 10",
                20L,
                "Customer One",
                status == InvoiceStatus.DRAFT ? null : 15L,
                status == InvoiceStatus.DRAFT ? null : "Floor Staff",
                status,
                new java.math.BigDecimal("120000.00"),
                new java.math.BigDecimal("30000.00"),
                java.math.BigDecimal.ZERO,
                new java.math.BigDecimal(totalAmount),
                status == InvoiceStatus.DRAFT ? null : Instant.parse("2026-03-28T03:00:00Z"),
                status == InvoiceStatus.PAID ? Instant.parse("2026-03-28T03:01:00Z") : null,
                null,
                Instant.parse("2026-03-28T03:00:00Z"),
                Instant.parse("2026-03-28T03:00:00Z")
        );
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
