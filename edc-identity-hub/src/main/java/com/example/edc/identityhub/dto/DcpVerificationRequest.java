package com.example.edc.identityhub.dto;

import jakarta.validation.constraints.NotBlank;

public record DcpVerificationRequest(@NotBlank String presentationId) {
}
