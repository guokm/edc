package com.example.edc.controlplane.dto;

import jakarta.validation.constraints.NotBlank;

public record DataPlaneRegistrationRequest(
        @NotBlank String id,
        @NotBlank String publicApiBaseUrl,
        @NotBlank String controlApiBaseUrl,
        String protocol
) {
}
