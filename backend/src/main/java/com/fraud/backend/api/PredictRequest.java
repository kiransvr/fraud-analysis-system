package com.fraud.backend.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PredictRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String customerId,
        String deviceId,
        String merchantCategory,
        String channel,
        String countryCode) {
}
