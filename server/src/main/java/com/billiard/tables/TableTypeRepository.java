package com.billiard.tables;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TableTypeRepository
        extends JpaRepository<TableType, Long>, JpaSpecificationExecutor<TableType> {

    Optional<TableType> findByNameIgnoreCase(String name);
}
