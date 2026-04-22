package com.example.edc.operator.dto;

import java.time.Instant;

public record CurrentUserResponse(
        UserAccountView user,
        Instant expiresAt
) {
}
