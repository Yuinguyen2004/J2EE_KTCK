package com.billiard.customers;

import com.billiard.customers.dto.CustomerResponse;
import com.billiard.customers.dto.CustomerUpsertRequest;
import com.billiard.memberships.MembershipTier;
import com.billiard.memberships.MembershipTierRepository;
import com.billiard.shared.web.PageRequestFactory;
import com.billiard.shared.web.PageResponse;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerCrudService {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "memberSince", "memberSince",
            "fullName", "user.fullName",
            "email", "user.email",
            "active", "user.active",
            "updatedAt", "updatedAt"
    );

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final MembershipTierRepository membershipTierRepository;

    public CustomerCrudService(
            CustomerRepository customerRepository,
            UserRepository userRepository,
            MembershipTierRepository membershipTierRepository
    ) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.membershipTierRepository = membershipTierRepository;
    }

    @Transactional
    public CustomerResponse create(CustomerUpsertRequest request) {
        User user = findCustomerUser(request.userId());
        customerRepository.findByUser_Id(user.getId()).ifPresent(existing -> {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Customer already exists for the selected user"
            );
        });

        Customer customer = new Customer();
        applyRequest(customer, user, request);
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerUpsertRequest request) {
        Customer customer = findEntity(id);
        User user = findCustomerUser(request.userId());
        customerRepository.findByUser_Id(user.getId())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Customer already exists for the selected user"
                    );
                });

        applyRequest(customer, user, request);
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse updateActive(Long id, boolean active) {
        Customer customer = findEntity(id);
        customer.getUser().setActive(active);
        userRepository.save(customer.getUser());
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(
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
                "createdAt",
                SORT_FIELDS
        );
        Page<Customer> customers = customerRepository.findAll(buildSpecification(q), pageable);
        return PageResponse.from(customers, this::toResponse);
    }

    private void applyRequest(Customer customer, User user, CustomerUpsertRequest request) {
        customer.setUser(user);
        customer.setMembershipTier(findMembershipTier(request.membershipTierId()));
        customer.setNotes(normalizeNullable(request.notes()));
        customer.setMemberSince(
                request.memberSince() == null ? Instant.now() : request.memberSince()
        );
    }

    private CustomerResponse toResponse(Customer customer) {
        MembershipTier membershipTier = customer.getMembershipTier();
        User user = customer.getUser();

        return new CustomerResponse(
                customer.getId(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.isActive(),
                membershipTier == null ? null : membershipTier.getId(),
                membershipTier == null ? null : membershipTier.getName(),
                customer.getNotes(),
                customer.getMemberSince(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }

    private Customer findEntity(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Customer not found"
        ));
    }

    private User findCustomerUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User not found"
        ));

        if (user.getRole() != UserRole.CUSTOMER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only CUSTOMER users can be linked to customer profiles"
            );
        }

        return user;
    }

    private MembershipTier findMembershipTier(Long membershipTierId) {
        if (membershipTierId == null) {
            return null;
        }

        return membershipTierRepository.findById(membershipTierId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Membership tier not found"
                ));
    }

    private Specification<Customer> buildSpecification(String q) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(q)) {
                return criteriaBuilder.conjunction();
            }

            query.distinct(true);
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            var userJoin = root.join("user", JoinType.INNER);
            var membershipJoin = root.join("membershipTier", JoinType.LEFT);
            var predicates = new ArrayList<Predicate>();
            predicates.add(criteriaBuilder.like(criteriaBuilder.lower(userJoin.get("email")), pattern, '\\'));
            predicates.add(
                    criteriaBuilder.like(criteriaBuilder.lower(userJoin.get("fullName")), pattern, '\\')
            );
            predicates.add(criteriaBuilder.like(criteriaBuilder.lower(userJoin.get("phone")), pattern, '\\'));
            predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("notes")), pattern, '\\'));
            predicates.add(
                    criteriaBuilder.like(criteriaBuilder.lower(membershipJoin.get("name")), pattern, '\\')
            );
            return criteriaBuilder.or(predicates.toArray(Predicate[]::new));
        };
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
}
