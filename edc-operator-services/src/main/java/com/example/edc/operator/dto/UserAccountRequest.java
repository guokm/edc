package com.example.edc.operator.dto;

import jakarta.validation.constraints.NotBlank;

public record UserAccountRequest(
        @NotBlank String username,
        @NotBlank String displayName,
        @NotBlank String organizationId,
        @NotBlank String participantId,
        @NotBlank String roleCode,
        @NotBlank String password,
        String status
) {
}
