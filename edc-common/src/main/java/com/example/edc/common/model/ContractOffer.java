package com.example.edc.common.model;

import java.time.LocalDateTime;

public record ContractOffer(
        String id,
        String assetId,
        String policyId,
        String providerId,
        LocalDateTime createdAt
) {
}
