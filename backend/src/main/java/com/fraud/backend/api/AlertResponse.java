package com.fraud.backend.api;

public record AlertResponse(
        Long id,
        String externalTransactionId,
        String alertType,
        String severity,
        String status,
        String reason) {
}
