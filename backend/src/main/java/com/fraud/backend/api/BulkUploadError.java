package com.fraud.backend.api;

public record BulkUploadError(int rowNumber, String externalTransactionId, String message) {
}