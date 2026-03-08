package com.example.edc.federated.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CatalogSyncRequest(
        @NotBlank String datasetId,
        @NotBlank String assetId,
        @NotBlank String assetName,
        String assetDescription,
        @NotBlank String classification,
        @NotBlank String ownerId,
        Map<String, Object> metadata,
        @NotBlank String offerId,
        @NotBlank String policyId,
        @NotBlank String providerId,
        String createdAt
) {
}
