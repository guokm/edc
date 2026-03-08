package com.example.edc.common.model;

import java.util.List;

public record CatalogEntry(
        String datasetId,
        Asset asset,
        List<ContractOffer> offers
) {
}
