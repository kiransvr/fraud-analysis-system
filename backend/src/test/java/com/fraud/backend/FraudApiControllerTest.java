package com.fraud.backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fraud.backend.api.AlertResponse;
import com.fraud.backend.api.PredictResponse;
import com.fraud.backend.api.TransactionResponse;
import com.fraud.backend.controller.AlertController;
import com.fraud.backend.controller.ApiExceptionHandler;
import com.fraud.backend.controller.PredictionController;
import com.fraud.backend.controller.TransactionController;
import com.fraud.backend.service.FraudService;

import java.util.Objects;

@WebMvcTest(controllers = { TransactionController.class, PredictionController.class, AlertController.class })
@Import(ApiExceptionHandler.class)
class FraudApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FraudService fraudService;

    @Test
    void predictReturnsScoredResponse() throws Exception {
        given(fraudService.predict(any())).willReturn(new PredictResponse(0.98d, "HIGH", "baseline-0.1"));

        String body = """
                {
                  "amount": 9800.00,
                  "customerId": "cust-001",
                  "deviceId": "dev-001",
                  "merchantCategory": "electronics",
                  "channel": "web",
                  "countryCode": "US"
                }
                """;

        mockMvc.perform(post("/predict")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.probability").value(0.98d))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"));
    }

    @Test
    void listTransactionsReturnsPagedData() throws Exception {
        TransactionResponse response = new TransactionResponse(
                "tx-001",
                new BigDecimal("9800.00"),
                "USD",
                "SCORED",
                0.98d,
                "HIGH",
                "baseline-0.1",
                1L);

        given(fraudService.listTransactions(eq(20), eq(0), eq("HIGH"))).willReturn(List.of(response));

        mockMvc.perform(get("/transactions")
                        .param("limit", "20")
                        .param("offset", "0")
                        .param("riskLevel", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalTransactionId").value("tx-001"))
                .andExpect(jsonPath("$[0].riskLevel").value("HIGH"));
    }

    @Test
    void getTransactionReturnsNotFoundWhenMissing() throws Exception {
        given(fraudService.getTransaction(eq("tx-missing")))
                .willThrow(new NoSuchElementException("Transaction not found: tx-missing"));

        mockMvc.perform(get("/transactions/tx-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void listAlertsReturnsResults() throws Exception {
        AlertResponse alert = new AlertResponse(1L, "tx-001", "FRAUD_RISK", "HIGH", "OPEN", "High fraud probability");
        given(fraudService.listAlerts()).willReturn(List.of(alert));

        mockMvc.perform(get("/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alertType").value("FRAUD_RISK"))
                .andExpect(jsonPath("$[0].severity").value("HIGH"));
    }

    @Test
    void createTransactionRejectsInvalidPayload() throws Exception {
        String body = """
                {
                  "externalTransactionId": "tx-002",
                  "customerId": "cust-001",
                  "amount": 0,
                  "currencyCode": "US"
                }
                """;

        mockMvc.perform(post("/transactions")
                                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransactionReturnsServerErrorWhenMlScoringFails() throws Exception {
        String body = """
                {
                  "externalTransactionId": "tx-003",
                  "customerId": "cust-001",
                  "amount": 7500,
                  "currencyCode": "USD",
                  "countryCode": "US"
                }
                """;

        given(fraudService.createAndScoreTransaction(any()))
                .willThrow(new IllegalStateException("ML service unavailable"));

        mockMvc.perform(post("/transactions")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("ML service unavailable"));
    }
}
