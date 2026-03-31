package com.billiard.billing;

import com.billiard.billing.dto.InvoiceResponse;
import com.billiard.shared.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/sessions/{sessionId}/invoice")
    public ResponseEntity<InvoiceResponse> generate(@PathVariable Long sessionId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invoiceService.generateForSession(sessionId));
    }

    @GetMapping("/invoices")
    public PageResponse<InvoiceResponse> list(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return invoiceService.list(sessionId, status, q, page, size, sortBy, direction);
    }

    @GetMapping("/invoices/{invoiceId}")
    public InvoiceResponse get(@PathVariable Long invoiceId) {
        return invoiceService.get(invoiceId);
    }

    @PostMapping("/invoices/{invoiceId}/issue")
    public InvoiceResponse issue(@PathVariable Long invoiceId, Authentication authentication) {
        return invoiceService.issue(invoiceId, authentication.getName());
    }

    @PostMapping("/invoices/{invoiceId}/pay")
    public InvoiceResponse pay(@PathVariable Long invoiceId, Authentication authentication) {
        return invoiceService.pay(invoiceId, authentication.getName());
    }

    @PostMapping("/invoices/{invoiceId}/void")
    public InvoiceResponse voidInvoice(
            @PathVariable Long invoiceId,
            Authentication authentication
    ) {
        return invoiceService.voidInvoice(invoiceId, authentication.getName());
    }
}
