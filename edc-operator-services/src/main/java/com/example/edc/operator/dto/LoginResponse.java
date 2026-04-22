package com.example.edc.operator.dto;

import java.time.Instant;

public record LoginResponse(
        String token,
        UserAccountView user,
        Instant expiresAt
) {
}
