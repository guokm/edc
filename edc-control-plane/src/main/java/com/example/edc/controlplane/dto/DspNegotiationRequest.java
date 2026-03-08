package com.example.edc.controlplane.dto;

import jakarta.validation.constraints.NotBlank;

public record DspNegotiationRequest(
        @NotBlank String assetId,
        @NotBlank String consumerId,
        @NotBlank String offerId
) {
}
