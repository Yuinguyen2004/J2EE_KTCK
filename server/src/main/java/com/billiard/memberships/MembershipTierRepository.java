package com.billiard.memberships;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MembershipTierRepository
        extends JpaRepository<MembershipTier, Long>, JpaSpecificationExecutor<MembershipTier> {

    Optional<MembershipTier> findByNameIgnoreCase(String name);
}
