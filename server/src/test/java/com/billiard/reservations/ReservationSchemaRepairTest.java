package com.billiard.reservations;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

@ExtendWith(MockitoExtension.class)
class ReservationSchemaRepairTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void runDropsNotNullWhenLegacyReservationSchemaStillRequiresTable() throws Exception {
        ReservationSchemaRepair repair = new ReservationSchemaRepair(jdbcTemplate);
        when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<String>>any()
        )).thenReturn("NO");

        repair.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate).execute("alter table reservations alter column table_id drop not null");
    }

    @Test
    void runSkipsAlterWhenReservationTableAlreadyAllowsNullTableAssignments() throws Exception {
        ReservationSchemaRepair repair = new ReservationSchemaRepair(jdbcTemplate);
        when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<String>>any()
        )).thenReturn("YES");

        repair.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }
}
