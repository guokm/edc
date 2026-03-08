package com.example.edc.issuer.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;

public record IssuanceRequest(
        @NotBlank String type,
        @NotBlank String issuer,
        Map<String, Object> claims,
        Instant expiresAt
) {
}
