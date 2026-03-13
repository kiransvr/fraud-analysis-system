package com.fraud.backend.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fraud.backend.api.PredictRequest;
import com.fraud.backend.config.MlServiceProperties;

@Component
public class MlClient {
    private final RestClient restClient;
    private final int maxPredictAttempts;
    private final long retryBackoffMs;

    public MlClient(RestClient mlRestClient, MlServiceProperties properties) {
        this.restClient = mlRestClient;
        this.maxPredictAttempts = properties.maxAttemptsOrDefault();
        this.retryBackoffMs = properties.backoffMsOrDefault();
    }

    public MlClient(RestClient mlRestClient) {
        this(mlRestClient, new MlServiceProperties(null, null, null));
    }

    public MlPrediction predict(PredictRequest request) {
        MlPredictPayload payload = new MlPredictPayload(
                request.amount(),
                request.customerId(),
                request.deviceId(),
                request.merchantCategory(),
                request.channel(),
                request.countryCode());

        MlPredictResponse response = null;
        for (int attempt = 1; attempt <= maxPredictAttempts; attempt++) {
            try {
                response = restClient.post()
                        .uri("/predict")
                        .body(payload)
                        .retrieve()
                        .body(MlPredictResponse.class);
                break;
            } catch (RestClientResponseException ex) {
                boolean retryableServerError = ex.getStatusCode().is5xxServerError();
                boolean hasAttemptsLeft = attempt < maxPredictAttempts;
                if (!retryableServerError || !hasAttemptsLeft) {
                    throw ex;
                }
                sleepBackoff(attempt * retryBackoffMs);
            }
        }

        if (response == null || response.probability() == null) {
            throw new IllegalStateException("ML service returned invalid response");
        }

        double probability = response.probability();
        String modelVersion = response.modelVersion() == null ? "unknown" : response.modelVersion();
        return new MlPrediction(probability, modelVersion);
    }

    private void sleepBackoff(long backoffMs) {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry ML prediction", interruptedException);
        }
    }

    public record MlPrediction(double probability, String modelVersion) {
    }

    private record MlPredictPayload(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("customer_id") String customerId,
            @JsonProperty("device_id") String deviceId,
            @JsonProperty("merchant_category") String merchantCategory,
            @JsonProperty("channel") String channel,
            @JsonProperty("country_code") String countryCode) {
    }

    private record MlPredictResponse(
            @JsonProperty("probability") Double probability,
            @JsonProperty("model_version") String modelVersion) {
    }
}
