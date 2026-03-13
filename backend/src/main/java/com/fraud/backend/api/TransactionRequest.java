package com.fraud.backend.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransactionRequest(
        @NotBlank String externalTransactionId,
        @NotBlank String customerId,
        String deviceId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currencyCode,
        String merchantId,
        String merchantCategory,
        String channel,
        @Size(min = 2, max = 2) String countryCode,
        OffsetDateTime transactionTime) {
}
