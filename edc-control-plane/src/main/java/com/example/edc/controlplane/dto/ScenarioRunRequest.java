package com.example.edc.controlplane.dto;

import jakarta.validation.constraints.Min;

public record ScenarioRunRequest(
        @Min(1) Integer assetCount,
        String consumerId
) {
}
