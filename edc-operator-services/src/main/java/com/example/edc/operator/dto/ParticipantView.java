package com.example.edc.operator.dto;

import java.time.Instant;

public record ParticipantView(
        String id,
        String participantId,
        String organizationId,
        String displayName,
        String roleType,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
