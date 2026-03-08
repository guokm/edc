package com.example.edc.common.model;

import java.util.Map;

public record Policy(
        String id,
        String type,
        Map<String, Object> rules
) {
}
