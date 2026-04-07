package com.billiard.customers;

import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthenticatedCustomerService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final TransactionTemplate writeTransactionTemplate;

    public AuthenticatedCustomerService(
            UserRepository userRepository,
            CustomerRepository customerRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        this.writeTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
                .orElseGet(() -> createMissingCustomerProfile(user));
    }

    private Customer createMissingCustomerProfile(User user) {
        return writeTransactionTemplate.execute(status -> {
            User managedUser = userRepository.findById(user.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Authenticated user not found"
                    ));
            Customer customer = new Customer();
            customer.setUser(managedUser);
            Instant memberSince = managedUser.getCreatedAt() != null
                    ? managedUser.getCreatedAt()
                    : user.getCreatedAt();
            customer.setMemberSince(memberSince != null ? memberSince : Instant.now());

            try {
                return customerRepository.save(customer);
            } catch (DataIntegrityViolationException exception) {
                return customerRepository.findByUser_Id(managedUser.getId())
                        .orElseThrow(() -> exception);
            }
        });
    }
}
