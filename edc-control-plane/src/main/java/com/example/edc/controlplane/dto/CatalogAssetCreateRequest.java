package com.example.edc.controlplane.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CatalogAssetCreateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotBlank String classification,
        @NotBlank String ownerId,
        @NotBlank String policyId,
        @NotBlank String providerId,
        Map<String, Object> metadata
) {
}
