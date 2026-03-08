package com.example.edc.operator.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record AuditRequest(
        @NotBlank String eventType,
        @NotBlank String actorId,
        Map<String, Object> payload,
        String signature
) {
}
