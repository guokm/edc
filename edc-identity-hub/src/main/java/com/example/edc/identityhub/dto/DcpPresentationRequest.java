package com.example.edc.identityhub.dto;

import jakarta.validation.constraints.NotBlank;

public record DcpPresentationRequest(
        @NotBlank String credentialId,
        String audience
) {
}
