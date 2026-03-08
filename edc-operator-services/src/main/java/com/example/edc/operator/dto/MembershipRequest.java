package com.example.edc.operator.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record MembershipRequest(
        @NotBlank String participantId,
        @NotBlank String level,
        Instant validTo
) {
}
