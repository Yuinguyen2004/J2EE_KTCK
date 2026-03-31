package com.billiard.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthenticatedCustomerServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    private AuthenticatedCustomerService authenticatedCustomerService;

    @BeforeEach
    void setUp() {
        authenticatedCustomerService = new AuthenticatedCustomerService(userRepository, customerRepository);
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
    void getRequiredCustomerRejectsMissingCustomerProfile() {
        User user = buildUser(14L, "missing-profile@example.com", UserRole.CUSTOMER, true);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(customerRepository.findByUser_Id(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticatedCustomerService.getRequiredCustomer(user.getEmail()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static User buildUser(Long id, String email, UserRole role, boolean active) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setActive(active);
        return user;
    }
}
