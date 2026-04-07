package com.billiard.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthenticatedCustomerServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private AuthenticatedCustomerService authenticatedCustomerService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        authenticatedCustomerService = new AuthenticatedCustomerService(
                userRepository,
                customerRepository,
                transactionManager
        );
    }

    @Test
    void getRequiredCustomerReturnsActiveCustomerProfile() {
        User user = buildUser(11L, "customer@example.com", UserRole.CUSTOMER, true);
        Customer customer = new Customer();
        customer.setId(21L);
        customer.setUser(user);

        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(customerRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(customer));

        Customer resolved = authenticatedCustomerService.getRequiredCustomer(user.getEmail());

        assertThat(resolved).isSameAs(customer);
    }

    @Test
    void getRequiredCustomerRejectsNonCustomerRole() {
        User staff = buildUser(12L, "staff@example.com", UserRole.STAFF, true);
        when(userRepository.findByEmailIgnoreCase(staff.getEmail())).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> authenticatedCustomerService.getRequiredCustomer(staff.getEmail()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getRequiredCustomerRejectsInactiveCustomer() {
        User inactiveCustomer = buildUser(13L, "inactive@example.com", UserRole.CUSTOMER, false);
        when(userRepository.findByEmailIgnoreCase(inactiveCustomer.getEmail())).thenReturn(Optional.of(inactiveCustomer));

        assertThatThrownBy(() -> authenticatedCustomerService.getRequiredCustomer(inactiveCustomer.getEmail()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getRequiredCustomerAutoCreatesMissingCustomerProfile() {
        User detachedUser = buildUser(14L, "missing-profile@example.com", UserRole.CUSTOMER, true);
        detachedUser.setCreatedAt(Instant.parse("2026-04-06T14:00:00Z"));
        User managedUser = buildUser(14L, detachedUser.getEmail(), UserRole.CUSTOMER, true);
        managedUser.setCreatedAt(detachedUser.getCreatedAt());
        managedUser.setVersion(3L);

        when(userRepository.findByEmailIgnoreCase(detachedUser.getEmail())).thenReturn(Optional.of(detachedUser));
        when(userRepository.findById(detachedUser.getId())).thenReturn(Optional.of(managedUser));
        doReturn(Optional.empty(), Optional.of(buildCustomer(24L, managedUser)))
                .when(customerRepository)
                .findByUser_Id(detachedUser.getId());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer created = invocation.getArgument(0);
            created.setId(24L);
            return created;
        });

        Customer resolved = authenticatedCustomerService.getRequiredCustomer(detachedUser.getEmail());

        assertThat(resolved.getId()).isEqualTo(24L);
        assertThat(resolved.getUser()).isSameAs(managedUser);
        assertThat(resolved.getMemberSince()).isEqualTo(detachedUser.getCreatedAt());
        verify(userRepository).findById(detachedUser.getId());
    }

    private static User buildUser(Long id, String email, UserRole role, boolean active) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setActive(active);
        return user;
    }

    private static Customer buildCustomer(Long id, User user) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setUser(user);
        return customer;
    }
}
