package com.example.edc.common.model;

import java.time.Instant;
import java.util.Map;

public record Credential(
        String id,
        String type,
        String issuer,
        Map<String, Object> claims,
        Instant issuedAt,
        Instant expiresAt
) {
}
