package com.billiard.reservations;

import com.billiard.reservations.dto.ConfirmReservationRequest;
import com.billiard.reservations.dto.CreateReservationRequest;
import com.billiard.reservations.dto.ReservationResponse;
import com.billiard.reservations.dto.UpdateReservationRequest;
import com.billiard.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public PageResponse<ReservationResponse> list(
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return reservationService.list(tableId, customerId, status, q, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable Long id) {
        return reservationService.get(id);
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody CreateReservationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.create(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ReservationResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReservationRequest request,
            Authentication authentication
    ) {
        return reservationService.update(id, request, authentication.getName());
    }

    @PostMapping("/{id}/confirm")
    public ReservationResponse confirm(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmReservationRequest request,
            Authentication authentication
    ) {
        return reservationService.confirm(id, request, authentication.getName());
    }
}
