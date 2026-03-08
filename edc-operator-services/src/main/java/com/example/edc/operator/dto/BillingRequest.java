package com.example.edc.operator.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record BillingRequest(
        @NotBlank String agreementId,
        @NotBlank String pricingModel,
        double amount,
        @NotBlank String currency,
        Instant periodStart,
        Instant periodEnd
) {
}
