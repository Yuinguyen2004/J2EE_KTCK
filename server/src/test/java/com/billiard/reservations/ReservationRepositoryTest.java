package com.billiard.reservations;

import static org.assertj.core.api.Assertions.assertThat;

import com.billiard.auth.AuthProvider;
import com.billiard.customers.Customer;
import com.billiard.customers.CustomerRepository;
import com.billiard.shared.config.JpaAuditingConfig;
import com.billiard.tables.BilliardTable;
import com.billiard.tables.BilliardTableRepository;
import com.billiard.tables.TableStatus;
import com.billiard.tables.TableType;
import com.billiard.tables.TableTypeRepository;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ReservationRepositoryTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TableTypeRepository tableTypeRepository;

    @Autowired
    private BilliardTableRepository billiardTableRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByIdForUpdateDoesNotFetchVersionedUserAssociations() {
        User customerUser = userRepository.save(buildUser("customer@example.com", "Customer One", UserRole.CUSTOMER));
        User staffUser = userRepository.save(buildUser("staff@example.com", "Shift Lead", UserRole.STAFF));

        Customer customer = new Customer();
        customer.setUser(customerUser);
        customer.setMemberSince(Instant.parse("2026-04-01T00:00:00Z"));
        customer = customerRepository.save(customer);

        TableType tableType = new TableType();
        tableType.setName("Pool");
        tableType.setDescription("Standard pool table");
        tableType = tableTypeRepository.save(tableType);

        BilliardTable table = new BilliardTable();
        table.setName("Ban 1");
        table.setTableType(tableType);
        table.setStatus(TableStatus.AVAILABLE);
        table.setFloorPositionX(1);
        table.setFloorPositionY(1);
        table = billiardTableRepository.save(table);

        Reservation reservation = new Reservation();
        reservation.setCustomer(customer);
        reservation.setStaff(staffUser);
        reservation.setTable(table);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setReservedFrom(Instant.parse("2026-04-10T09:30:00Z"));
        reservation.setReservedTo(Instant.parse("2026-04-10T11:00:00Z"));
        reservation.setPartySize(4);
        reservation = reservationRepository.saveAndFlush(reservation);

        jdbcTemplate.update(
                "update users set version = null where id in (?, ?)",
                customerUser.getId(),
                staffUser.getId()
        );
        entityManager.clear();

        Reservation lockedReservation = reservationRepository.findByIdForUpdate(reservation.getId()).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(lockedReservation.getId()).isEqualTo(reservation.getId());
        assertThat(persistenceUnitUtil.isLoaded(lockedReservation, "customer")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(lockedReservation, "staff")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(lockedReservation, "table")).isFalse();
    }

    private static User buildUser(String email, String fullName, UserRole role) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);
        user.setProvider(AuthProvider.LOCAL);
        user.setActive(true);
        return user;
    }
}
