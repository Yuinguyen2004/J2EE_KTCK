package com.billiard.reservations;

import com.billiard.reservations.dto.CustomerReservationRequest;
import com.billiard.reservations.dto.ReservationResponse;
import com.billiard.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/reservations")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerReservationController {

    private final ReservationService reservationService;

    public CustomerReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public PageResponse<ReservationResponse> list(
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication
    ) {
        return reservationService.listOwned(
                status,
                q,
                page,
                size,
                sortBy,
                direction,
                authentication.getName()
        );
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable Long id, Authentication authentication) {
        return reservationService.getOwned(id, authentication.getName());
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody CustomerReservationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.createCustomerRequest(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ReservationResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CustomerReservationRequest request,
            Authentication authentication
    ) {
        return reservationService.updateCustomerRequest(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id, Authentication authentication) {
        reservationService.cancelCustomerRequest(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
