package com.example.edc.operator.dto;

import java.time.Instant;

public record UserAccountView(
        String id,
        String username,
        String displayName,
        String organizationId,
        String participantId,
        String roleCode,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
