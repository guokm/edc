package com.example.edc.dataplane.dto;

import jakarta.validation.constraints.NotBlank;

public record TransferSignalRequest(@NotBlank String transferProcessId) {
}
