package com.example.edc.common.model;

public record ContractDefinition(
        String id,
        String assetSelector,
        String contractPolicyId,
        String accessPolicyId
) {
}
