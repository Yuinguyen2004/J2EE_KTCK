package com.billiard.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.billiard.reservations.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class BilliardTableCrudServiceTest {

    @Mock
    private BilliardTableRepository billiardTableRepository;

    @Mock
    private TableTypeRepository tableTypeRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private BilliardTableCrudService billiardTableCrudService;
    private BilliardTable table;

    @BeforeEach
    void setUp() {
        billiardTableCrudService = new BilliardTableCrudService(
                billiardTableRepository,
                tableTypeRepository,
                reservationRepository,
                Clock.fixed(Instant.parse("2026-03-28T09:30:00Z"), ZoneId.of("UTC"))
        );

        table = buildTable(10L, "Table 10", TableStatus.AVAILABLE);
    }

    @Test
    void listProjectsReservedStatusWhenActiveReservationExists() {
        when(billiardTableRepository.findAll(
                org.mockito.ArgumentMatchers.any(org.springframework.data.jpa.domain.Specification.class),
                org.mockito.ArgumentMatchers.any(PageRequest.class)
        ))
                .thenReturn(new PageImpl<>(List.of(table), PageRequest.of(0, 20), 1));
        when(reservationRepository.findDistinctTableIdsWithActiveReservations(
                anyCollection(),
                anyCollection(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        ))
                .thenReturn(List.of(table.getId()));

        var page = billiardTableCrudService.list(null, 0, 20, "name", "ASC");

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().status()).isEqualTo(TableStatus.RESERVED);
    }

    @Test
    void getClearsStaleReservedStatusWhenNoActiveReservationExists() {
        table.setStatus(TableStatus.RESERVED);
        when(billiardTableRepository.findById(table.getId())).thenReturn(Optional.of(table));
        when(reservationRepository.findDistinctTableIdsWithActiveReservations(
                anyCollection(),
                anyCollection(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        ))
                .thenReturn(List.of());

        var response = billiardTableCrudService.get(table.getId());

        assertThat(response.status()).isEqualTo(TableStatus.AVAILABLE);
    }

    private static BilliardTable buildTable(Long id, String name, TableStatus status) {
        TableType tableType = new TableType();
        tableType.setId(1L);
        tableType.setName("Pool");
        tableType.setActive(true);

        BilliardTable table = new BilliardTable();
        table.setId(id);
        table.setName(name);
        table.setTableType(tableType);
        table.setStatus(status);
        table.setActive(true);
        table.setFloorPositionX(0);
        table.setFloorPositionY(0);
        return table;
    }
}
