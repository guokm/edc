package com.example.edc.common.model;

import java.time.Instant;

public record UsageEvent(
        String id,
        String agreementId,
        long bytes,
        long durationMillis,
        Instant recordedAt
) {
}
