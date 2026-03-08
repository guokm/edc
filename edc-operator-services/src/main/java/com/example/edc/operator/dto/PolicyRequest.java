package com.example.edc.operator.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record PolicyRequest(
        @NotBlank String type,
        Map<String, Object> rules
) {
}
