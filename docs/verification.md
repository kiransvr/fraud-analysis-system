# Verification Plan

## System Checks

1. Backend builds and integration tests pass.
2. ML training reaches accuracy >= 92% and recall >= 95%.
3. /predict returns probability and mapped risk level.
4. /explain returns top feature contributions.
5. Frontend flow works: submit transaction -> score -> alert.
6. Docker Compose starts all services with healthy connectivity.
7. Kubernetes deployment exposes endpoints and pods are ready.
8. CI workflow passes on push and pull request.

## Test Matrix

- Backend: unit tests + Spring integration tests + DB integration
- ML: unit tests + model metric tests + inference contract tests
- Frontend: unit tests + API integration mocks + Playwright e2e
