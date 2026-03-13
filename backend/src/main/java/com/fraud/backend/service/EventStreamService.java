package com.fraud.backend.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class EventStreamService {

    private static final long SSE_TIMEOUT_MS = 0L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));

        sendSingle(emitter, "connected", Map.of("connectedAt", OffsetDateTime.now().toString()));
        return emitter;
    }

    public void publishTransactionScored(String externalTransactionId, String riskLevel) {
        publish("transaction-scored", Map.of(
                "externalTransactionId", externalTransactionId,
                "riskLevel", riskLevel,
                "publishedAt", OffsetDateTime.now().toString()));
    }

    public void publishBulkUpload(int processedRows, int failedRows, int alertsCreated) {
        publish("bulk-upload", Map.of(
                "processedRows", processedRows,
                "failedRows", failedRows,
                "alertsCreated", alertsCreated,
                "publishedAt", OffsetDateTime.now().toString()));
    }

    private void publish(String eventName, Object payload) {
        for (SseEmitter emitter : emitters) {
            if (!sendSingle(emitter, eventName, payload)) {
                emitters.remove(emitter);
            }
        }
    }

    private boolean sendSingle(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(Objects.requireNonNull(eventName))
                    .data(Objects.requireNonNull(payload)));
            return true;
        } catch (IOException ioException) {
            emitter.completeWithError(ioException);
            return false;
        }
    }
}