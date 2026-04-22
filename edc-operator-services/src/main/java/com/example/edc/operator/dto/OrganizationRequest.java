package com.example.edc.operator.dto;

import jakarta.validation.constraints.NotBlank;

public record OrganizationRequest(
        @NotBlank String name,
        String creditCode,
        String contactName,
        String contactPhone,
        String contactEmail,
        String status
) {
}
