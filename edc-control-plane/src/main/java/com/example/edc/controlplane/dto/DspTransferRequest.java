package com.example.edc.controlplane.dto;

import jakarta.validation.constraints.NotBlank;

public record DspTransferRequest(
        @NotBlank String agreementId,
        @NotBlank String protocol,
        String dataPlaneId
) {
}
