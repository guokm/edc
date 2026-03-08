package com.example.edc.common.model;

import java.util.Map;

public record Asset(
        String id,
        String name,
        String description,
        String classification,
        String owner,
        Map<String, Object> metadata
) {
}
