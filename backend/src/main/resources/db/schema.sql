-- SQL Server DDL for AI Fraud Detection Analytics

CREATE TABLE customers (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_customer_id NVARCHAR(64) NOT NULL UNIQUE,
    full_name NVARCHAR(200) NOT NULL,
    email NVARCHAR(255) NULL,
    phone NVARCHAR(50) NULL,
    kyc_status NVARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE TABLE devices (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    device_fingerprint NVARCHAR(200) NOT NULL UNIQUE,
    device_type NVARCHAR(50) NULL,
    os_name NVARCHAR(50) NULL,
    ip_address NVARCHAR(64) NULL,
    first_seen_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    last_seen_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE TABLE model_versions (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    model_name NVARCHAR(100) NOT NULL,
    version NVARCHAR(50) NOT NULL,
    artifact_uri NVARCHAR(500) NOT NULL,
    metrics_json NVARCHAR(MAX) NULL,
    is_active BIT NOT NULL DEFAULT 0,
    trained_at DATETIME2 NOT NULL,
    deployed_at DATETIME2 NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_model_name_version UNIQUE (model_name, version)
);

CREATE TABLE transactions (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_transaction_id NVARCHAR(64) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    device_id BIGINT NULL,
    amount DECIMAL(18,2) NOT NULL,
    currency_code NVARCHAR(3) NOT NULL,
    merchant_id NVARCHAR(64) NULL,
    merchant_category NVARCHAR(100) NULL,
    channel NVARCHAR(30) NULL,
    country_code NVARCHAR(2) NULL,
    transaction_time DATETIME2 NOT NULL,
    status NVARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    fraud_probability DECIMAL(5,4) NULL,
    risk_level NVARCHAR(10) NULL,
    model_version_id BIGINT NULL,
    scored_at DATETIME2 NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_transactions_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_transactions_device FOREIGN KEY (device_id) REFERENCES devices(id),
    CONSTRAINT fk_transactions_model_version FOREIGN KEY (model_version_id) REFERENCES model_versions(id)
);

CREATE TABLE fraud_alerts (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    alert_type NVARCHAR(50) NOT NULL,
    severity NVARCHAR(20) NOT NULL,
    status NVARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reason NVARCHAR(500) NULL,
    assigned_to NVARCHAR(100) NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    resolved_at DATETIME2 NULL,
    CONSTRAINT fk_fraud_alerts_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

CREATE TABLE audit_logs (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    actor NVARCHAR(100) NOT NULL,
    action NVARCHAR(100) NOT NULL,
    entity_type NVARCHAR(100) NOT NULL,
    entity_id NVARCHAR(64) NULL,
    metadata_json NVARCHAR(MAX) NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE INDEX ix_transactions_customer_time ON transactions(customer_id, transaction_time DESC);
CREATE INDEX ix_transactions_risk_level ON transactions(risk_level);
CREATE INDEX ix_fraud_alerts_status_created ON fraud_alerts(status, created_at DESC);
CREATE INDEX ix_audit_logs_actor_created ON audit_logs(actor, created_at DESC);
