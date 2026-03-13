package com.fraud.backend.api;

public record PredictResponse(
        Double probability,
        String riskLevel,
        String modelVersion) {
}
