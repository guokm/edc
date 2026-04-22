package com.example.edc.operator.dto;

import jakarta.validation.constraints.NotBlank;

public record ParticipantRequest(
        @NotBlank String participantId,
        @NotBlank String organizationId,
        @NotBlank String displayName,
        @NotBlank String roleType,
        String status
) {
}
