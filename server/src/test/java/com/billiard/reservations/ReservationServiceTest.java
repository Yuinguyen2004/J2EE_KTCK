package com.billiard.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.billiard.customers.AuthenticatedCustomerService;
import com.billiard.customers.Customer;
import com.billiard.customers.CustomerRepository;
import com.billiard.memberships.MembershipTier;
import com.billiard.reservations.dto.ConfirmReservationRequest;
import com.billiard.reservations.dto.CreateReservationRequest;
import com.billiard.reservations.dto.CustomerReservationRequest;
import com.billiard.reservations.dto.ReservationResponse;
import com.billiard.reservations.dto.UpdateReservationRequest;
import com.billiard.shared.websocket.FloorEvents;
import com.billiard.shared.web.PageResponse;
import com.billiard.tables.BilliardTable;
import com.billiard.tables.BilliardTableRepository;
import com.billiard.tables.TableStatus;
import com.billiard.tables.TableType;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private BilliardTableRepository billiardTableRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticatedCustomerService authenticatedCustomerService;

    @Mock
    private FloorEvents floorEvents;

    private MutableClock clock;
    private ReservationService reservationService;

    private User staff;
    private Customer customer;
    private Customer otherCustomer;
    private BilliardTable table;
    private final AtomicLong reservationIdSequence = new AtomicLong(900L);
    private final Map<Long, Reservation> storedReservations = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-03-28T09:00:00Z"), ZoneId.of("UTC"));
        reservationService = new ReservationService(
                reservationRepository,
                billiardTableRepository,
                customerRepository,
                userRepository,
                authenticatedCustomerService,
                floorEvents,
                clock
        );

        staff = buildUser(10L, "staff@example.com", "Shift Lead", UserRole.STAFF, true);
        customer = buildCustomer(20L, "customer@example.com", "Customer One", true);
        otherCustomer = buildCustomer(21L, "other@example.com", "Customer Two", true);
        table = buildTable(30L, "Table 7", true);

        lenient().when(userRepository.findByEmailIgnoreCase(staff.getEmail()))
                .thenReturn(Optional.of(staff));
        lenient().when(billiardTableRepository.findById(table.getId()))
                .thenReturn(Optional.of(table));
        lenient().when(billiardTableRepository.findByIdForUpdate(table.getId()))
                .thenReturn(Optional.of(table));
        lenient().when(customerRepository.findById(customer.getId()))
                .thenReturn(Optional.of(customer));
        lenient().when(authenticatedCustomerService.getRequiredCustomer(customer.getUser().getEmail()))
                .thenReturn(customer);
        lenient().when(authenticatedCustomerService.getRequiredCustomer(otherCustomer.getUser().getEmail()))
                .thenReturn(otherCustomer);
        lenient().when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            if (reservation.getId() == null) {
                reservation.setId(reservationIdSequence.getAndIncrement());
                reservation.setCreatedAt(clock.instant());
            }
            reservation.setUpdatedAt(clock.instant());
            storedReservations.put(reservation.getId(), reservation);
            return reservation;
        });
        lenient().when(reservationRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long reservationId = invocation.getArgument(0);
            return Optional.ofNullable(storedReservations.get(reservationId));
        });
        lenient().when(reservationRepository.findByIdForUpdate(anyLong())).thenAnswer(invocation -> {
            Long reservationId = invocation.getArgument(0);
            return Optional.ofNullable(storedReservations.get(reservationId));
        });
        lenient().when(reservationRepository
                .existsByTable_IdAndStatusInAndReservedFromLessThanAndReservedToGreaterThan(
                        anyLong(),
                        any(),
                        any(),
                        any()
                )).thenReturn(false);
        lenient().when(reservationRepository
                .existsByTable_IdAndIdNotAndStatusInAndReservedFromLessThanAndReservedToGreaterThan(
                        anyLong(),
                        anyLong(),
                        any(),
                        any(),
                        any()
                )).thenReturn(false);
    }

    @Test
    void createStoresPendingReservationAndAssignedStaff() {
        ReservationResponse response = reservationService.create(
                new CreateReservationRequest(
                        table.getId(),
                        customer.getId(),
                        Instant.parse("2026-03-28T11:00:00Z"),
                        Instant.parse("2026-03-28T13:00:00Z"),
                        4,
                        "  birthday booking  "
                ),
                staff.getEmail()
        );

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.tableId()).isEqualTo(table.getId());
        assertThat(response.customerId()).isEqualTo(customer.getId());
        assertThat(response.staffId()).isEqualTo(staff.getId());
        assertThat(response.partySize()).isEqualTo(4);
        assertThat(response.notes()).isEqualTo("birthday booking");
        assertThat(response.checkedInAt()).isNull();
        org.mockito.Mockito.verify(floorEvents).reservationChanged(org.mockito.ArgumentMatchers.argThat(
                reservationResponse -> reservationResponse != null
                        && reservationResponse.id().equals(response.id())
                        && reservationResponse.status() == ReservationStatus.PENDING
                        && reservationResponse.tableId().equals(table.getId())
        ));
    }

    @Test
    void createRejectsOverlappingActiveReservation() {
        when(reservationRepository.existsByTable_IdAndStatusInAndReservedFromLessThanAndReservedToGreaterThan(
                table.getId(),
                List.of(
                        ReservationStatus.PENDING,
                        ReservationStatus.CONFIRMED,
                        ReservationStatus.CHECKED_IN
                ),
                Instant.parse("2026-03-28T13:00:00Z"),
                Instant.parse("2026-03-28T11:00:00Z")
        )).thenReturn(true);

        assertThatThrownBy(() -> reservationService.create(
                new CreateReservationRequest(
                        table.getId(),
                        customer.getId(),
                        Instant.parse("2026-03-28T11:00:00Z"),
                        Instant.parse("2026-03-28T13:00:00Z"),
                        2,
                        null
                ),
                staff.getEmail()
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateRejectsInvalidStatusTransition() {
        Reservation reservation = storeReservation(ReservationStatus.PENDING);

        assertThatThrownBy(() -> reservationService.update(
                reservation.getId(),
                new UpdateReservationRequest(
                        table.getId(),
                        customer.getId(),
                        ReservationStatus.CHECKED_IN,
                        reservation.getReservedFrom(),
                        reservation.getReservedTo(),
                        reservation.getPartySize(),
                        reservation.getNotes()
                ),
                staff.getEmail()
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateSetsCheckedInAtWhenReservationMovesToCheckedIn() {
        Reservation reservation = storeReservation(ReservationStatus.CONFIRMED);

        ReservationResponse response = reservationService.update(
                reservation.getId(),
                new UpdateReservationRequest(
                        table.getId(),
                        customer.getId(),
                        ReservationStatus.CHECKED_IN,
                        reservation.getReservedFrom(),
                        reservation.getReservedTo(),
                        6,
                        "arrived"
                ),
                staff.getEmail()
        );

        assertThat(response.status()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(response.checkedInAt()).isEqualTo(clock.instant());
        assertThat(response.partySize()).isEqualTo(6);
        assertThat(response.notes()).isEqualTo("arrived");
        org.mockito.Mockito.verify(floorEvents).reservationChanged(org.mockito.ArgumentMatchers.argThat(
                reservationResponse -> reservationResponse != null
                        && reservationResponse.id().equals(response.id())
                        && reservationResponse.status() == ReservationStatus.CHECKED_IN
                        && reservationResponse.checkedInAt().equals(clock.instant())
        ));
    }

    @Test
    void listReturnsPagedReservations() {
        Reservation created = storedReservations.get(reservationService.create(
                new CreateReservationRequest(
                        table.getId(),
                        customer.getId(),
                        Instant.parse("2026-03-28T14:00:00Z"),
                        Instant.parse("2026-03-28T16:00:00Z"),
                        3,
                        "vip lane"
                ),
                staff.getEmail()
        ).id());

        when(reservationRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(created), PageRequest.of(0, 20), 1));

        PageResponse<?> page = reservationService.list(
                table.getId(),
                customer.getId(),
                ReservationStatus.PENDING,
                "vip",
                0,
                20,
                "reservedFrom",
                "DESC"
        );

        assertThat(page.items()).hasSize(1);
        ReservationResponse first = (ReservationResponse) page.items().getFirst();
        assertThat(first.id()).isEqualTo(created.getId());
        assertThat(first.tableName()).isEqualTo(table.getName());
        assertThat(first.partySize()).isEqualTo(3);
        assertThat(first.customerName()).isEqualTo(customer.getUser().getFullName());
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void getReturnsStoredReservation() {
        Reservation reservation = storeReservation(ReservationStatus.CONFIRMED);

        ReservationResponse response = reservationService.get(reservation.getId());

        assertThat(response.id()).isEqualTo(reservation.getId());
        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.staffName()).isEqualTo(staff.getFullName());
    }

    @Test
    void getReturnsNullableTableFieldsForUnassignedReservationRequest() {
        Reservation reservation = storeReservation(ReservationStatus.PENDING, null, 5);

        ReservationResponse response = reservationService.get(reservation.getId());

        assertThat(response.id()).isEqualTo(reservation.getId());
        assertThat(response.tableId()).isNull();
        assertThat(response.tableName()).isNull();
        assertThat(response.tableStatus()).isNull();
        assertThat(response.partySize()).isEqualTo(5);
    }

    @Test
    void createCustomerRequestStoresPendingUnassignedReservation() {
        ReservationResponse response = reservationService.createCustomerRequest(
                new CustomerReservationRequest(
                        Instant.parse("2026-03-28T15:00:00Z"),
                        Instant.parse("2026-03-28T17:00:00Z"),
                        5,
                        "corner if possible"
                ),
                customer.getUser().getEmail()
        );

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.tableId()).isNull();
        assertThat(response.staffId()).isNull();
        assertThat(response.customerId()).isEqualTo(customer.getId());
        assertThat(response.partySize()).isEqualTo(5);
    }

    @Test
    void confirmAssignsTableAndStaffToPendingCustomerRequest() {
        Reservation reservation = storeReservation(ReservationStatus.PENDING, null, 4);

        ReservationResponse response = reservationService.confirm(
                reservation.getId(),
                new ConfirmReservationRequest(table.getId(), "assigned by staff"),
                staff.getEmail()
        );

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.tableId()).isEqualTo(table.getId());
        assertThat(response.staffId()).isEqualTo(staff.getId());
        assertThat(response.notes()).isEqualTo("assigned by staff");
    }

    @Test
    void cancelCustomerRequestMarksReservationCancelled() {
        Reservation reservation = storeReservation(ReservationStatus.PENDING, null, 2);

        reservationService.cancelCustomerRequest(reservation.getId(), customer.getUser().getEmail());

        assertThat(storedReservations.get(reservation.getId()).getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void getOwnedRejectsOtherCustomersReservationAccess() {
        Reservation reservation = storeReservation(ReservationStatus.PENDING, null, 2);

        assertThatThrownBy(() -> reservationService.getOwned(
                reservation.getId(),
                otherCustomer.getUser().getEmail()
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private Reservation storeReservation(ReservationStatus status) {
        return storeReservation(status, table, 2);
    }

    private Reservation storeReservation(ReservationStatus status, BilliardTable assignedTable, Integer partySize) {
        Reservation reservation = new Reservation();
        reservation.setTable(assignedTable);
        reservation.setCustomer(customer);
        reservation.setStaff(staff);
        reservation.setStatus(status);
        reservation.setReservedFrom(Instant.parse("2026-03-28T11:00:00Z"));
        reservation.setReservedTo(Instant.parse("2026-03-28T13:00:00Z"));
        reservation.setPartySize(partySize);
        reservation.setNotes("window seat");
        if (status == ReservationStatus.CHECKED_IN) {
            reservation.setCheckedInAt(clock.instant());
        }
        return reservationRepository.save(reservation);
    }

    private static User buildUser(
            Long id,
            String email,
            String fullName,
            UserRole role,
            boolean active
    ) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(active);
        return user;
    }

    private static Customer buildCustomer(Long id, String email, String fullName, boolean active) {
        User user = buildUser(40L, email, fullName, UserRole.CUSTOMER, active);
        MembershipTier membershipTier = new MembershipTier();
        membershipTier.setId(50L);
        membershipTier.setName("Silver");
        membershipTier.setActive(true);

        Customer customer = new Customer();
        customer.setId(id);
        customer.setUser(user);
        customer.setMembershipTier(membershipTier);
        return customer;
    }

    private static BilliardTable buildTable(Long id, String name, boolean active) {
        TableType tableType = new TableType();
        tableType.setId(60L);
        tableType.setName("Pool");
        tableType.setActive(true);

        BilliardTable billiardTable = new BilliardTable();
        billiardTable.setId(id);
        billiardTable.setName(name);
        billiardTable.setTableType(tableType);
        billiardTable.setStatus(TableStatus.AVAILABLE);
        billiardTable.setActive(active);
        return billiardTable;
    }

    private static final class MutableClock extends Clock {

        private final Instant currentInstant;
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
    }
}
