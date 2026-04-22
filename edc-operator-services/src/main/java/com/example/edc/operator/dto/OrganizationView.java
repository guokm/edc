package com.example.edc.operator.dto;

import java.time.Instant;

public record OrganizationView(
        String id,
        String name,
        String creditCode,
        String contactName,
        String contactPhone,
        String contactEmail,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
