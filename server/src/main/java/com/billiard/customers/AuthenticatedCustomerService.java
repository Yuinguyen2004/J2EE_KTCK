package com.billiard.customers;

import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthenticatedCustomerService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    public AuthenticatedCustomerService(UserRepository userRepository, CustomerRepository customerRepository) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public Customer getRequiredCustomer(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Authenticated user not found"
                ));

        if (user.getRole() != UserRole.CUSTOMER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only customers can access this resource"
            );
        }

        if (!user.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Inactive customers cannot access this resource"
            );
        }

        return customerRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Customer profile not found"
                ));
    }
}
