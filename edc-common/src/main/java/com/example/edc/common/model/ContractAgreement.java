package com.example.edc.common.model;

import java.time.Instant;

public record ContractAgreement(
        String id,
        String contractOfferId,
        String consumerId,
        String providerId,
        Instant validFrom,
        Instant validTo,
        String status
) {
}
