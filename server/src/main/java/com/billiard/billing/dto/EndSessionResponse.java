package com.billiard.billing.dto;

public record EndSessionResponse(
        TableSessionResponse session,
        InvoiceResponse invoice
) {
}
