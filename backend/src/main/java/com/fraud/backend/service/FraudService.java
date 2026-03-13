package com.fraud.backend.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fraud.backend.api.AlertResponse;
import com.fraud.backend.api.BulkUploadError;
import com.fraud.backend.api.BulkUploadResponse;
import com.fraud.backend.api.PredictRequest;
import com.fraud.backend.api.PredictResponse;
import com.fraud.backend.api.TransactionRequest;
import com.fraud.backend.api.TransactionResponse;
import com.fraud.backend.repository.FraudRepository;
import com.fraud.backend.service.MlClient.MlPrediction;

@Service
public class FraudService {

    private static final int MAX_BULK_UPLOAD_ROWS = 5000;
    private static final int MAX_BULK_UPLOAD_ERRORS = 50;

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

    public long countTransactions() {
        return repository.countTransactions();
    }

    @Transactional
    public BulkUploadResponse uploadTransactionsCsv(MultipartFile file, boolean replaceExisting) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        if (replaceExisting) {
            repository.clearOperationalData();
        }

        List<BulkUploadError> errors = new ArrayList<>();
        int totalRows = 0;
        int processedRows = 0;
        int alertsCreated = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean headerSkipped = false;
            int rowNumber = 0;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                String trimmed = line.trim();
                String normalized = trimmed.replace("\uFEFF", "");
                if (trimmed.isEmpty()) {
                    continue;
                }

                if (!headerSkipped) {
                    headerSkipped = true;
                    if (normalized.toLowerCase().startsWith("externaltransactionid,")) {
                        continue;
                    }
                }

                totalRows++;
                if (totalRows > MAX_BULK_UPLOAD_ROWS) {
                    throw new IllegalArgumentException("Maximum upload limit is " + MAX_BULK_UPLOAD_ROWS + " rows");
                }

                try {
                    TransactionRequest request = parseCsvLine(normalized);
                    TransactionResponse response = createAndScoreTransaction(request);
                    processedRows++;
                    if (response.alertId() != null) {
                        alertsCreated++;
                    }
                } catch (Exception ex) {
                    if (errors.size() < MAX_BULK_UPLOAD_ERRORS) {
                        String externalTransactionId = extractTransactionId(normalized);
                        errors.add(new BulkUploadError(rowNumber, externalTransactionId, toUploadFriendlyError(ex)));
                    }
                }
            }
        } catch (IOException ioException) {
            throw new IllegalStateException("Unable to read upload file", ioException);
        }

        int failedRows = totalRows - processedRows;
        return new BulkUploadResponse(totalRows, processedRows, failedRows, alertsCreated, errors);
    }

    private TransactionRequest parseCsvLine(String line) {
        String[] columns = line.split(",", -1);
        if (columns.length < 9) {
            throw new IllegalArgumentException("CSV row must contain at least 9 columns");
        }

        String externalTransactionId = requiredValue(columns, 0, "externalTransactionId");
        String customerId = requiredValue(columns, 1, "customerId");
        BigDecimal amount = parseAmount(columns[3]);
        String currencyCode = requiredValue(columns, 4, "currencyCode");

        String deviceId = optionalValue(columns, 2);
        String merchantId = optionalValue(columns, 5);
        String merchantCategory = optionalValue(columns, 6);
        String channel = optionalValue(columns, 7);
        String countryCode = optionalValue(columns, 8);
        OffsetDateTime transactionTime = parseTransactionTime(optionalValue(columns, 9));

        return new TransactionRequest(
                externalTransactionId,
                customerId,
                deviceId,
                amount,
                currencyCode,
                merchantId,
                merchantCategory,
                channel,
                countryCode,
                transactionTime);
    }

    private String requiredValue(String[] columns, int index, String fieldName) {
        String value = optionalValue(columns, index);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return value;
    }

    private String optionalValue(String[] columns, int index) {
        if (index >= columns.length) {
            return null;
        }
        String value = columns[index].trim();
        return value.isBlank() ? null : value;
    }

    private BigDecimal parseAmount(String rawAmount) {
        String amountValue = rawAmount == null ? "" : rawAmount.trim();
        if (amountValue.isBlank()) {
            throw new IllegalArgumentException("Missing required field: amount");
        }
        try {
            return new BigDecimal(amountValue);
        } catch (NumberFormatException numberFormatException) {
            throw new IllegalArgumentException("Invalid amount: " + amountValue);
        }
    }

    private OffsetDateTime parseTransactionTime(String rawTime) {
        if (rawTime == null || rawTime.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawTime);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid transactionTime format. Use ISO-8601 format");
        }
    }

    private String extractTransactionId(String line) {
        String[] columns = line.split(",", -1);
        if (columns.length == 0) {
            return null;
        }
        String transactionId = columns[0].trim();
        return transactionId.isEmpty() ? null : transactionId;
    }

    private String toUploadFriendlyError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Unknown upload error";
        }

        String lowercase = message.toLowerCase();
        if (lowercase.contains("unique key constraint") || lowercase.contains("duplicate key value")) {
            return "Duplicate externalTransactionId";
        }

        return message;
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
