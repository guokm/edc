package com.example.edc.common.model;

import java.time.Instant;

public record Membership(
        String id,
        String participantId,
        String level,
        Instant validFrom,
        Instant validTo,
        String status
) {
}
