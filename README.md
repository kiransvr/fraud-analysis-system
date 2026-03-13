# AI Fraud Detection Analytics

Three-tier fraud detection platform with:
- Backend API: Spring Boot
- Frontend: Angular
- ML service: FastAPI + XGBoost
- Database: Microsoft SQL Server
- Deployment: Docker Compose + Kubernetes

## Repository Structure

- backend/ - Java Spring Boot API, persistence, alerts, auth, audit
- frontend/ - Angular dashboard and role-based views
- ml/ - Python model training + prediction and explainability APIs
- deployment/ - Dockerfiles, Compose, Kubernetes manifests
- docs/ - Architecture, compliance, operations, and testing guides

## Implementation Status

- [x] Phase 1 scaffolding
- [x] Initial architecture and data flow draft
- [x] Initial SQL Server DDL draft
- [x] Deployment and CI skeletons
- [x] Backend API implementation (health, predict, transactions, alerts)
- [x] ML service implementation (baseline inference + risk mapping)
- [x] Angular dashboard implementation
- [x] End-to-end integration and hardening (unit tests + Playwright smoke)

## Local Development

Before running Compose, create `deployment/.env` from `deployment/.env.example` and set secure credentials.

Optional backend ML retry tuning:

- `ML_SERVICE_MAX_ATTEMPTS` (default `3`): maximum retries for transient 5xx responses from ML service.
- `ML_SERVICE_RETRY_BACKOFF_MS` (default `50`): base backoff in milliseconds between retry attempts.

1. Start full stack:

```bash
docker compose -f deployment/docker-compose.yml up -d --build
```

2. Run backend tests:

```bash
cd backend
mvn -B test
```

If Maven is not installed locally, use:

```bash
docker run --rm -v "${PWD}:/workspace" -w /workspace/backend maven:3.9.9-eclipse-temurin-21 mvn -B test
```

3. Run ML tests:

```bash
cd ml
pytest -q
```

If Python/pytest is not installed locally, use:

```bash
docker run --rm -v "${PWD}:/workspace" -w /workspace/ml python:3.11-slim sh -lc "pip install -r requirements.txt && PYTHONPATH=/workspace pytest -q"
```

4. Run frontend unit tests:

```bash
cd frontend
npm ci
npm test -- --watch=false --browsers=ChromeHeadless --no-progress
```

5. Run frontend Playwright e2e smoke:

```bash
cd frontend
npx playwright install chromium
$env:E2E_BASE_URL='http://localhost:4200'; npm run e2e
```

6. Stop full stack:

```bash
docker compose -f deployment/docker-compose.yml down --volumes
```

## Bulk Data Upload

You can upload transaction batches from the dashboard or directly via API.

- Endpoint: `POST /transactions/upload`
- Content type: `multipart/form-data`
- Form field name: `file`
- Max rows per file: `5000`
- Optional query param: `replaceExisting=true` to clear current dashboard data before import

Required CSV header:

```text
externalTransactionId,customerId,deviceId,amount,currencyCode,merchantId,merchantCategory,channel,countryCode,transactionTime
```

Sample files are included here:

- `docs/samples/transactions-upload-fresh-500.csv` (500 rows)

PowerShell upload example:

```powershell
curl.exe -X POST "http://localhost:8080/transactions/upload?replaceExisting=true" -F "file=@docs/samples/transactions-upload-fresh-500.csv"
```

## Verification Targets

- Model metrics: accuracy >= 92%, recall >= 95%
- Transaction score response with risk mapping: Low/Medium/High
- Explainability endpoint returns feature attributions
- Compose stack connectivity and data persistence
- CI pass on pull requests and pushes (including frontend-e2e)

## CI Job Summary

The workflow in `.github/workflows/ci.yml` runs the following jobs on push and pull requests to `main`:

- `backend-tests`: runs Spring Boot backend tests with Maven.
- `ml-tests`: installs Python dependencies and runs ML pytest suite.
- `frontend-build`: installs frontend dependencies and builds production Angular bundle.
- `frontend-tests`: runs frontend unit tests in headless Chrome.
- `frontend-e2e`: starts the full compose stack, waits for backend/frontend readiness, runs Playwright smoke tests, and uploads artifacts on failure.
- `docker-build-check`: validates Docker Compose configuration syntax.

## Troubleshooting CI

- Backend readiness timeout in `frontend-e2e`:
	- Check backend container logs in the job output and verify `/api/health` becomes reachable.
	- Confirm `deployment/.env` values are valid and SQL Server starts cleanly.
- Frontend readiness timeout in `frontend-e2e`:
	- Verify Angular image build completed in compose startup logs.
	- Confirm port `4200` is exposed and Nginx starts without config errors.
- Playwright e2e failures:
	- Download `playwright-artifacts` from the workflow run.
	- Inspect `frontend/playwright-report/` and `frontend/test-results/` for traces/screenshots.
- Dependency install instability (`npm`/`pip`):
	- Re-run once to rule out transient registry/network issues.
	- If recurring, pin versions in lockfiles/requirements and review upstream release changes.
- Compose validation failures (`docker-build-check`):
	- Run `docker compose -f deployment/docker-compose.yml config` locally to reproduce syntax errors.

## Release Readiness Checklist

Use this checklist before merging to `main`:

- Backend tests pass (`mvn -B test` in `backend`).
- ML tests pass (`pytest -q` in `ml`).
- Frontend unit tests pass (`npm test -- --watch=false --browsers=ChromeHeadless --no-progress` in `frontend`).
- Frontend Playwright smoke tests pass (`npm run e2e` in `frontend` with `E2E_BASE_URL` set).
- Docker Compose stack starts and health endpoints are reachable.
- Docker Compose stack shuts down cleanly (`down --volumes`).
- CI workflow is green for the branch/PR.
- README and env examples are updated for any new runtime configuration.
