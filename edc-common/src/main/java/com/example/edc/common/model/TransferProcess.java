package com.example.edc.common.model;

import java.time.Instant;

public record TransferProcess(
        String id,
        String agreementId,
        String protocol,
        String dataPlaneId,
        String state,
        Instant createdAt
) {
}
