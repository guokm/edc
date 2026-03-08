package com.example.edc.controlplane.dto;

import jakarta.validation.constraints.NotBlank;

public record ContractNegotiationRequest(
        @NotBlank String assetId,
        @NotBlank String consumerId,
        @NotBlank String offerId
) {
}
