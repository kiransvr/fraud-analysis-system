package com.fraud.backend.api;

import java.math.BigDecimal;

public record TransactionResponse(
        String externalTransactionId,
        BigDecimal amount,
        String currencyCode,
        String status,
        Double fraudProbability,
        String riskLevel,
        String modelVersion,
        Long alertId) {
}
