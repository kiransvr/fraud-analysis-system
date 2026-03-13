package com.fraud.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fraud.backend.api.PredictRequest;
import com.fraud.backend.config.MlServiceProperties;
import com.fraud.backend.service.MlClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class MlClientTest {

    @Test
    void predictParsesProbabilityAndModelVersion() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/predict", new JsonHandler("{\"probability\":0.73,\"model_version\":\"baseline-0.1\"}"));
        server.start();

        try {
            int port = server.getAddress().getPort();
            RestClient restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
            MlClient client = new MlClient(restClient);

            PredictRequest request = new PredictRequest(
                    new BigDecimal("1000.00"),
                    "cust-001",
                    "dev-001",
                    "retail",
                    "web",
                    "US");

            MlClient.MlPrediction prediction = client.predict(request);

            assertThat(prediction.probability()).isEqualTo(0.73d);
            assertThat(prediction.modelVersion()).isEqualTo("baseline-0.1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void predictThrowsWhenProbabilityIsMissing() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/predict", new JsonHandler("{\"model_version\":\"baseline-0.1\"}"));
        server.start();

        try {
            int port = server.getAddress().getPort();
            RestClient restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
            MlClient client = new MlClient(restClient);

            PredictRequest request = new PredictRequest(
                    new BigDecimal("1000.00"),
                    "cust-001",
                    "dev-001",
                    "retail",
                    "web",
                    "US");

            assertThatThrownBy(() -> client.predict(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("ML service returned invalid response");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void predictThrowsWhenMlServiceReturns503() throws Exception {
        AtomicInteger requestCount = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/predict", new CountingStatusHandler(503, "service unavailable", requestCount));
        server.start();

        try {
            int port = server.getAddress().getPort();
            RestClient restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
            MlClient client = new MlClient(restClient);

            PredictRequest request = new PredictRequest(
                    new BigDecimal("1000.00"),
                    "cust-001",
                    "dev-001",
                    "retail",
                    "web",
                    "US");

            assertThatThrownBy(() -> client.predict(request))
                    .isInstanceOf(RestClientResponseException.class)
                    .hasMessageContaining("503");
            assertThat(requestCount.get()).isEqualTo(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void predictRetriesOn503ThenSucceeds() throws Exception {
        AtomicInteger requestCount = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/predict", new FlakyPredictHandler(2, requestCount));
        server.start();

        try {
            int port = server.getAddress().getPort();
            RestClient restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
            MlClient client = new MlClient(restClient);

            PredictRequest request = new PredictRequest(
                    new BigDecimal("1000.00"),
                    "cust-001",
                    "dev-001",
                    "retail",
                    "web",
                    "US");

            MlClient.MlPrediction prediction = client.predict(request);

            assertThat(prediction.probability()).isEqualTo(0.73d);
            assertThat(prediction.modelVersion()).isEqualTo("baseline-0.1");
            assertThat(requestCount.get()).isEqualTo(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void predictHonorsConfiguredMaxAttempts() throws Exception {
        AtomicInteger requestCount = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/predict", new CountingStatusHandler(503, "service unavailable", requestCount));
        server.start();

        try {
            int port = server.getAddress().getPort();
            RestClient restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
            MlClient client = new MlClient(restClient, new MlServiceProperties(null, 2, 0L));

            PredictRequest request = new PredictRequest(
                    new BigDecimal("1000.00"),
                    "cust-001",
                    "dev-001",
                    "retail",
                    "web",
                    "US");

            assertThatThrownBy(() -> client.predict(request))
                    .isInstanceOf(RestClientResponseException.class)
                    .hasMessageContaining("503");

            assertThat(requestCount.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    private static final class JsonHandler implements HttpHandler {
        private final String json;

        private JsonHandler(String json) {
            this.json = json;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        }
    }

    private static final class StatusHandler implements HttpHandler {
        private final int statusCode;
        private final String body;

        private StatusHandler(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        }
    }

    private static final class CountingStatusHandler implements HttpHandler {
        private final int statusCode;
        private final String body;
        private final AtomicInteger requestCount;

        private CountingStatusHandler(int statusCode, String body, AtomicInteger requestCount) {
            this.statusCode = statusCode;
            this.body = body;
            this.requestCount = requestCount;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        }
    }

    private static final class FlakyPredictHandler implements HttpHandler {
        private final int failuresBeforeSuccess;
        private final AtomicInteger requestCount;

        private FlakyPredictHandler(int failuresBeforeSuccess, AtomicInteger requestCount) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
            this.requestCount = requestCount;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int currentAttempt = requestCount.incrementAndGet();
            if (currentAttempt <= failuresBeforeSuccess) {
                byte[] failureBytes = "service unavailable".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(503, failureBytes.length);
                exchange.getResponseBody().write(failureBytes);
                exchange.close();
                return;
            }

            byte[] successBytes = "{\"probability\":0.73,\"model_version\":\"baseline-0.1\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, successBytes.length);
            exchange.getResponseBody().write(successBytes);
            exchange.close();
        }
    }
}