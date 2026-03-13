# Backend Service (Spring Boot)

## Planned Modules

- ingestion: transaction intake and validation
- prediction: ML service client and score orchestration
- alerting: threshold logic and alert lifecycle
- auth: user/role management
- audit: immutable security/compliance trail

## Planned Endpoints

- POST /transactions
- POST /predict
- GET /alerts
- GET /alerts/{id}
- GET /admin/health

## Database Artifacts

- SQL Server DDL: src/main/resources/db/schema.sql

## Next Implementation Steps

1. Initialize Spring Boot app with Maven and dependencies.
2. Add JPA entities and repositories matching schema.sql.
3. Add OpenAPI documentation and global exception handlers.
4. Add ML HTTP client and risk mapping policy.
5. Add tests (unit + integration with H2 or SQL Server test container).
