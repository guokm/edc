package com.example.edc.controlplane.dto;

import jakarta.validation.constraints.NotBlank;

public record TransferRequest(
        @NotBlank String agreementId,
        @NotBlank String protocol,
        String dataPlaneId
) {
}
