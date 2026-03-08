package com.example.edc.common.model;

public record DataAddress(
        String type,
        String endpoint,
        String authRef
) {
}
