package com.example.edc.identityhub.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;

public record CredentialRequest(
        @NotBlank String type,
        @NotBlank String issuer,
        Map<String, Object> claims,
        Instant expiresAt,
        String issuanceId
) {
}
