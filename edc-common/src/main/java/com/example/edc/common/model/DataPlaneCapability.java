package com.example.edc.common.model;

import java.util.List;

public record DataPlaneCapability(
        String id,
        List<String> protocols,
        List<String> dataTypes,
        List<String> sourceTypes
) {
}
