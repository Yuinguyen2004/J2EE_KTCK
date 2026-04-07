package com.billiard.reservations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReservationSchemaRepair implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationSchemaRepair.class);

    private final JdbcTemplate jdbcTemplate;

    public ReservationSchemaRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        String nullable = jdbcTemplate.query(
                """
                select is_nullable
                from information_schema.columns
                where table_name = 'reservations'
                  and column_name = 'table_id'
                """,
                rs -> rs.next() ? rs.getString("is_nullable") : null
        );

        if ("NO".equalsIgnoreCase(nullable)) {
            jdbcTemplate.execute("alter table reservations alter column table_id drop not null");
            LOGGER.info("Relaxed reservations.table_id to nullable for customer reservation requests");
        }
    }
}
