package com.example.edc.common.model;

public record Participant(
        String id,
        String did,
        String status,
        String tier,
        String region
) {
}
