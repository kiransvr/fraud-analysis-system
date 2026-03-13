package com.fraud.backend.api;

import java.util.List;

public record BulkUploadResponse(
        int totalRows,
        int processedRows,
        int failedRows,
        int alertsCreated,
        List<BulkUploadError> errors) {
}