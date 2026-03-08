package com.example.edc.controlplane.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record PolicyEvaluationRequest(
        @NotBlank String policyId,
        Map<String, Object> claims,
        Map<String, Object> constraints
) {
}
