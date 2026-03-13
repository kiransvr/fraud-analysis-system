package com.fraud.backend.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fraud.backend.api.AlertResponse;
import com.fraud.backend.api.PredictRequest;
import com.fraud.backend.api.PredictResponse;
import com.fraud.backend.api.TransactionRequest;
import com.fraud.backend.api.TransactionResponse;
import com.fraud.backend.repository.FraudRepository;
import com.fraud.backend.service.MlClient.MlPrediction;

@Service
public class FraudService {

    private final FraudRepository repository;
    private final MlClient mlClient;

    public FraudService(FraudRepository repository, MlClient mlClient) {
        this.repository = repository;
        this.mlClient = mlClient;
    }

    public PredictResponse predict(PredictRequest request) {
        MlPrediction prediction = mlClient.predict(request);
        return new PredictResponse(
                prediction.probability(),
                mapRisk(prediction.probability()),
                prediction.modelVersion());
    }

    @Transactional
    public TransactionResponse createAndScoreTransaction(TransactionRequest request) {
        long customerId = repository.upsertCustomer(request.customerId());
        Long deviceId = repository.upsertDevice(request.deviceId());

        OffsetDateTime transactionTime = request.transactionTime() == null
                ? OffsetDateTime.now(ZoneOffset.UTC)
                : request.transactionTime();

        long transactionId = repository.insertTransaction(
                request.externalTransactionId(),
                customerId,
                deviceId,
                request.amount(),
                request.currencyCode().toUpperCase(),
                request.merchantId(),
                request.merchantCategory(),
                request.channel(),
                request.countryCode(),
                transactionTime);

        PredictResponse prediction = predict(new PredictRequest(
                request.amount(),
                request.customerId(),
                request.deviceId(),
                request.merchantCategory(),
                request.channel(),
                request.countryCode()));

        long modelVersionId = repository.upsertModelVersion(prediction.modelVersion());
        repository.updateTransactionScore(transactionId, prediction.probability(), prediction.riskLevel(), modelVersionId);

        Long alertId = null;
        if ("HIGH".equals(prediction.riskLevel())) {
            alertId = repository.createAlert(
                    transactionId,
                    "HIGH",
                    "High fraud probability: " + prediction.probability());
        }

        repository.insertAuditLog(
                "system",
                "TRANSACTION_SCORED",
                "transaction",
                request.externalTransactionId(),
                "{\"risk\":\"" + prediction.riskLevel() + "\",\"probability\":" + prediction.probability() + "}");

        return new TransactionResponse(
                request.externalTransactionId(),
                request.amount(),
                request.currencyCode().toUpperCase(),
                "SCORED",
                prediction.probability(),
                prediction.riskLevel(),
                prediction.modelVersion(),
                alertId);
    }

    public List<AlertResponse> listAlerts() {
        return repository.findAlerts();
    }

    public TransactionResponse getTransaction(String externalTransactionId) {
        return repository.findTransactionByExternalId(externalTransactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + externalTransactionId));
    }

    public List<TransactionResponse> listTransactions(int limit, int offset, String riskLevel) {
        String normalizedRiskLevel = riskLevel == null ? null : riskLevel.toUpperCase();
        return repository.listTransactions(limit, offset, normalizedRiskLevel);
    }

    private String mapRisk(double probability) {
        if (probability < 0.40d) {
            return "LOW";
        }
        if (probability < 0.75d) {
            return "MEDIUM";
        }
        return "HIGH";
    }
}
