package com.billiard.orders;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MenuItemRepository
        extends JpaRepository<MenuItem, Long>, JpaSpecificationExecutor<MenuItem> {

    Optional<MenuItem> findByNameIgnoreCase(String name);
}
