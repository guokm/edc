package com.example.edc.common.model;

import java.time.Instant;
import java.util.Map;

public record AuditRecord(
        String id,
        String eventType,
        String actorId,
        Map<String, Object> payload,
        Instant createdAt,
        String signature
) {
}
