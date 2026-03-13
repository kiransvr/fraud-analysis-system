package com.fraud.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fraud.backend.api.PredictRequest;
import com.fraud.backend.api.PredictResponse;
import com.fraud.backend.api.TransactionRequest;
import com.fraud.backend.api.TransactionResponse;
import com.fraud.backend.repository.FraudRepository;
import com.fraud.backend.service.FraudService;
import com.fraud.backend.service.MlClient;
import com.fraud.backend.service.MlClient.MlPrediction;

@ExtendWith(MockitoExtension.class)
class FraudServiceBoundaryTest {

    @Mock
    private FraudRepository repository;

    @Mock
    private MlClient mlClient;

    @Test
    void predictMapsExactPointFourToMedium() {
        FraudService fraudService = new FraudService(repository, mlClient);
        PredictRequest request = new PredictRequest(new BigDecimal("4000.00"), "cust-001", "dev-001", "retail", "web", "US");

        given(mlClient.predict(any())).willReturn(new MlPrediction(0.40d, "baseline-0.1"));

        PredictResponse response = fraudService.predict(request);

        assertThat(response.riskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void predictMapsExactPointSevenFiveToHigh() {
        FraudService fraudService = new FraudService(repository, mlClient);
        PredictRequest request = new PredictRequest(new BigDecimal("7500.00"), "cust-001", "dev-001", "retail", "web", "US");

        given(mlClient.predict(any())).willReturn(new MlPrediction(0.75d, "baseline-0.1"));

        PredictResponse response = fraudService.predict(request);

        assertThat(response.riskLevel()).isEqualTo("HIGH");
    }

    @Test
    void createAndScoreTransactionAtPointFourDoesNotCreateAlert() {
        FraudService fraudService = new FraudService(repository, mlClient);
        TransactionRequest request = new TransactionRequest(
                "tx-bnd-040",
                "cust-040",
                "dev-040",
                new BigDecimal("4000.00"),
                "USD",
                "merchant-01",
                "retail",
                "web",
                "US",
                OffsetDateTime.now());

        given(repository.upsertCustomer("cust-040")).willReturn(10L);
        given(repository.upsertDevice("dev-040")).willReturn(20L);
        given(repository.insertTransaction(
                eq("tx-bnd-040"),
                eq(10L),
                eq(20L),
                eq(new BigDecimal("4000.00")),
                eq("USD"),
                eq("merchant-01"),
                eq("retail"),
                eq("web"),
                eq("US"),
                any(OffsetDateTime.class))).willReturn(30L);
        given(mlClient.predict(any())).willReturn(new MlPrediction(0.40d, "baseline-0.1"));
        given(repository.upsertModelVersion("baseline-0.1")).willReturn(40L);

        TransactionResponse response = fraudService.createAndScoreTransaction(request);

        assertThat(response.riskLevel()).isEqualTo("MEDIUM");
        assertThat(response.alertId()).isNull();
        verify(repository, never()).createAlert(any(Long.class), any(String.class), any(String.class));
    }

    @Test
    void createAndScoreTransactionAtPointSevenFiveCreatesAlert() {
        FraudService fraudService = new FraudService(repository, mlClient);
        TransactionRequest request = new TransactionRequest(
                "tx-bnd-075",
                "cust-075",
                "dev-075",
                new BigDecimal("7500.00"),
                "USD",
                "merchant-01",
                "retail",
                "web",
                "US",
                OffsetDateTime.now());

        given(repository.upsertCustomer("cust-075")).willReturn(11L);
        given(repository.upsertDevice("dev-075")).willReturn(21L);
        given(repository.insertTransaction(
                eq("tx-bnd-075"),
                eq(11L),
                eq(21L),
                eq(new BigDecimal("7500.00")),
                eq("USD"),
                eq("merchant-01"),
                eq("retail"),
                eq("web"),
                eq("US"),
                any(OffsetDateTime.class))).willReturn(31L);
        given(mlClient.predict(any())).willReturn(new MlPrediction(0.75d, "baseline-0.1"));
        given(repository.upsertModelVersion("baseline-0.1")).willReturn(41L);
        given(repository.createAlert(eq(31L), eq("HIGH"), any(String.class))).willReturn(99L);

        TransactionResponse response = fraudService.createAndScoreTransaction(request);

        assertThat(response.riskLevel()).isEqualTo("HIGH");
        assertThat(response.alertId()).isEqualTo(99L);
        verify(repository).createAlert(eq(31L), eq("HIGH"), any(String.class));
    }
}