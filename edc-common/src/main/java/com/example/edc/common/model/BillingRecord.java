package com.example.edc.common.model;

import java.time.Instant;

public record BillingRecord(
        String id,
        String agreementId,
        String pricingModel,
        double amount,
        String currency,
        Instant periodStart,
        Instant periodEnd
) {
}
