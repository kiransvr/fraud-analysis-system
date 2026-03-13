package com.fraud.backend.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.fraud.backend.api.AlertResponse;
import com.fraud.backend.api.TransactionResponse;

@Repository
public class FraudRepository {

    private final JdbcTemplate jdbcTemplate;

    public FraudRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long upsertCustomer(String externalCustomerId) {
        Long existing = jdbcTemplate.query(
                "SELECT id FROM customers WHERE external_customer_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                externalCustomerId);
        if (existing != null) {
            return existing;
        }

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO customers (external_customer_id, full_name)
                OUTPUT INSERTED.id
                VALUES (?, ?)
                """,
                Long.class,
                externalCustomerId,
                "Unknown " + externalCustomerId);
    }

    public Long upsertDevice(String deviceFingerprint) {
        if (deviceFingerprint == null || deviceFingerprint.isBlank()) {
            return null;
        }

        Long existing = jdbcTemplate.query(
                "SELECT id FROM devices WHERE device_fingerprint = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                deviceFingerprint);
        if (existing != null) {
            jdbcTemplate.update(
                    "UPDATE devices SET last_seen_at = SYSUTCDATETIME() WHERE id = ?",
                    existing);
            return existing;
        }

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO devices (device_fingerprint, last_seen_at)
                OUTPUT INSERTED.id
                VALUES (?, SYSUTCDATETIME())
                """,
                Long.class,
                deviceFingerprint);
    }

    public Long upsertModelVersion(String version) {
        Long existing = jdbcTemplate.query(
                "SELECT id FROM model_versions WHERE model_name = ? AND version = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                "xgboost",
                version);
        if (existing != null) {
            return existing;
        }

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO model_versions (model_name, version, artifact_uri, trained_at, is_active)
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, SYSUTCDATETIME(), 1)
                """,
                Long.class,
                "xgboost",
                version,
                "ml://runtime/" + version);
    }

    public long insertTransaction(
            String externalTransactionId,
            long customerId,
            Long deviceId,
            BigDecimal amount,
            String currencyCode,
            String merchantId,
            String merchantCategory,
            String channel,
            String countryCode,
            OffsetDateTime transactionTime) {

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO transactions (
                    external_transaction_id,
                    customer_id,
                    device_id,
                    amount,
                    currency_code,
                    merchant_id,
                    merchant_category,
                    channel,
                    country_code,
                    transaction_time,
                    status
                )
                OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED')
                """,
                Long.class,
                externalTransactionId,
                customerId,
                deviceId,
                amount,
                currencyCode,
                merchantId,
                merchantCategory,
                channel,
                countryCode,
                Timestamp.from(transactionTime.toInstant()));
    }

    public void updateTransactionScore(long transactionId, double probability, String riskLevel, long modelVersionId) {
        jdbcTemplate.update(
                """
                UPDATE transactions
                SET fraud_probability = ?,
                    risk_level = ?,
                    model_version_id = ?,
                    scored_at = SYSUTCDATETIME(),
                    status = 'SCORED',
                    updated_at = SYSUTCDATETIME()
                WHERE id = ?
                """,
                BigDecimal.valueOf(probability),
                riskLevel,
                modelVersionId,
                transactionId);
    }

    public Long createAlert(long transactionId, String severity, String reason) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO fraud_alerts (transaction_id, alert_type, severity, status, reason)
                OUTPUT INSERTED.id
                VALUES (?, 'FRAUD_RISK', ?, 'OPEN', ?)
                """,
                Long.class,
                transactionId,
                severity,
                reason);
    }

    public void insertAuditLog(String actor, String action, String entityType, String entityId, String metadataJson) {
        jdbcTemplate.update(
                """
                INSERT INTO audit_logs (actor, action, entity_type, entity_id, metadata_json)
                VALUES (?, ?, ?, ?, ?)
                """,
                actor,
                action,
                entityType,
                entityId,
                metadataJson);
    }

    public void clearOperationalData() {
        jdbcTemplate.update("DELETE FROM fraud_alerts");
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM devices");
        jdbcTemplate.update("DELETE FROM customers");
        jdbcTemplate.update("DELETE FROM model_versions");
    }

    public List<AlertResponse> findAlerts() {
        RowMapper<AlertResponse> mapper = (rs, rowNum) -> new AlertResponse(
                rs.getLong("id"),
                rs.getString("external_transaction_id"),
                rs.getString("alert_type"),
                rs.getString("severity"),
                rs.getString("status"),
                rs.getString("reason"));

        return jdbcTemplate.query(
                """
                SELECT a.id, a.alert_type, a.severity, a.status, a.reason, t.external_transaction_id
                FROM fraud_alerts a
                JOIN transactions t ON t.id = a.transaction_id
                ORDER BY a.created_at DESC
                """,
                mapper);
    }

    public long countTransactions() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Long.class);
        return count == null ? 0L : count;
    }

            public Optional<TransactionResponse> findTransactionByExternalId(String externalTransactionId) {
            RowMapper<TransactionResponse> mapper = (rs, rowNum) -> new TransactionResponse(
                rs.getString("external_transaction_id"),
                rs.getBigDecimal("amount"),
                rs.getString("currency_code"),
                rs.getString("status"),
                rs.getBigDecimal("fraud_probability") == null ? null : rs.getBigDecimal("fraud_probability").doubleValue(),
                rs.getString("risk_level"),
                rs.getString("model_version"),
                rs.getObject("alert_id") == null ? null : rs.getLong("alert_id"));

            return jdbcTemplate.query(
                """
                SELECT t.external_transaction_id,
                       t.amount,
                       t.currency_code,
                       t.status,
                       t.fraud_probability,
                       t.risk_level,
                       mv.version AS model_version,
                       a.id AS alert_id
                FROM transactions t
                LEFT JOIN model_versions mv ON mv.id = t.model_version_id
                LEFT JOIN fraud_alerts a ON a.transaction_id = t.id AND a.status = 'OPEN'
                WHERE t.external_transaction_id = ?
                """,
                mapper,
                externalTransactionId).stream().findFirst();
            }

            public List<TransactionResponse> listTransactions(int limit, int offset, String riskLevel) {
            RowMapper<TransactionResponse> mapper = (rs, rowNum) -> new TransactionResponse(
                rs.getString("external_transaction_id"),
                rs.getBigDecimal("amount"),
                rs.getString("currency_code"),
                rs.getString("status"),
                rs.getBigDecimal("fraud_probability") == null ? null : rs.getBigDecimal("fraud_probability").doubleValue(),
                rs.getString("risk_level"),
                rs.getString("model_version"),
                rs.getObject("alert_id") == null ? null : rs.getLong("alert_id"));

            if (riskLevel == null || riskLevel.isBlank()) {
                return jdbcTemplate.query(
                    """
                    SELECT t.external_transaction_id,
                       t.amount,
                       t.currency_code,
                       t.status,
                       t.fraud_probability,
                       t.risk_level,
                       mv.version AS model_version,
                       a.id AS alert_id
                    FROM transactions t
                    LEFT JOIN model_versions mv ON mv.id = t.model_version_id
                    LEFT JOIN fraud_alerts a ON a.transaction_id = t.id AND a.status = 'OPEN'
                    ORDER BY t.created_at DESC
                    OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                    """,
                    mapper,
                    offset,
                    limit);
            }

            return jdbcTemplate.query(
                """
                SELECT t.external_transaction_id,
                       t.amount,
                       t.currency_code,
                       t.status,
                       t.fraud_probability,
                       t.risk_level,
                       mv.version AS model_version,
                       a.id AS alert_id
                FROM transactions t
                LEFT JOIN model_versions mv ON mv.id = t.model_version_id
                LEFT JOIN fraud_alerts a ON a.transaction_id = t.id AND a.status = 'OPEN'
                WHERE t.risk_level = ?
                ORDER BY t.created_at DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """,
                mapper,
                riskLevel,
                offset,
                limit);
            }
}
