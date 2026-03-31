package com.billiard.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuthExchangeRequest(
        @NotBlank
        @Size(max = 255)
        String code
) {
}
