package com.example.edc.operator.dto;

import jakarta.validation.constraints.NotBlank;

public record BillingUsageCheckRequest(
        @NotBlank String participantId,
        @NotBlank String serviceCode
) {
}
