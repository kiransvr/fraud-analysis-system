# AI Fraud Detection Analytics - Executive Deployment Plan (Free Services Only)

## Executive Summary
This plan delivers a cost-conscious production rollout using open-source and self-hosted components. It is designed for clients who prioritize budget control and are prepared to own day-to-day infrastructure operations.

Business intent:
- Reach production with minimal platform licensing cost
- Maintain full control of hosting and runtime configuration
- Establish a practical baseline for secure and stable operations

## 1. Goal
This plan is for clients who want a production-capable deployment without paid platform subscriptions. It uses open-source tooling and self-hosted infrastructure.

Outcome:
- Client-facing application is online and reachable over HTTPS
- Transaction and alert data is durable and recoverable
- Operational monitoring and backup routines are in place
- Team has a clear runbook for support and incident response

Decision fit:
- Best for organizations with strong internal IT ownership
- Best when license cost minimization is a top procurement objective
- Best for pilot-to-production paths that can tolerate more manual operations

## 2. What Is Free in This Plan
- Linux VM(s) on existing client hardware or already-owned virtualization
- Docker Engine + Docker Compose
- Nginx or Caddy (reverse proxy + TLS automation)
- Prometheus + Grafana (observability)
- Loki + Promtail (log aggregation, optional)
- GitHub Actions free tier or self-hosted runner

Note:
- Domain name and public certificate authority setup may still have external cost depending on client environment.

## 3. Reference Architecture
- Frontend container: Angular static app via Nginx
- Backend container: Spring Boot API
- ML container: FastAPI inference service
- Database container: SQL Server container on dedicated persistent volume
- Reverse proxy: Caddy or Nginx in front of frontend/backend
- Monitoring: Prometheus + Grafana stack

## 4. Minimum Infrastructure Sizing
Small production starter:
- 1 VM, 8 vCPU, 16 GB RAM, 200 GB SSD
- Ubuntu 22.04 LTS
- Static private IP

Recommended split (better reliability):
- App VM: frontend, backend, ML, proxy
- Data VM: SQL Server + backup jobs

Executive note:
- Single-VM can accelerate go-live for pilot and early production.
- Two-VM split is recommended for stronger resilience and easier recovery.

## 5. Prerequisites
- DNS records created (example: fraud.client.com)
- Firewall rules open: 80/443 inbound to reverse proxy
- Outbound internet for package pull and container images
- Backup storage path (NFS share or mounted backup disk)

## 6. Deployment Steps (Up and Running)
### Step 1: Prepare host
1. Install Docker and Compose plugin.
2. Create deployment user and folders:
   - /opt/fraud-analysis-system
   - /opt/fraud-analysis-system/backups
3. Configure time sync and OS updates.

### Step 2: Pull application source
1. Clone repository to /opt/fraud-analysis-system.
2. Create deployment/.env from deployment/.env.example.
3. Set strong values for DB credentials and service configuration.

### Step 3: Start core stack
1. Run:
   - docker compose --env-file deployment/.env -f deployment/docker-compose.yml up -d --build
2. Verify:
   - docker compose --env-file deployment/.env -f deployment/docker-compose.yml ps

### Step 4: Configure reverse proxy + TLS
Option A (recommended): Caddy with automatic TLS.
Option B: Nginx + certbot.

Routing requirements:
- / -> frontend service
- /api/* or backend routes -> backend service

### Step 5: Smoke validation
Run these checks:
- GET /health (backend)
- GET /transactions/count
- GET /alerts
- GET /events/stream (SSE connected event)
- Frontend dashboard loads on HTTPS URL

### Step 6: Data persistence and backup
1. Confirm SQL data volume is persistent.
2. Add nightly DB backup job.
3. Add weekly restore drill in non-prod.

## 7. Operations Runbook
Daily:
- Check container health and restart count
- Check error logs and failed requests

Weekly:
- Verify backup completion
- Patch host OS and rotate logs

Monthly:
- Review capacity and storage growth
- Validate recovery steps in staging

## 8. Monitoring and Alerting (Free Stack)
- Prometheus scrapes backend and host metrics
- Grafana dashboard for API latency, error rates, CPU, memory
- Alertmanager rules:
  - backend down > 2 minutes
  - DB container down > 1 minute
  - error rate > threshold

## 9. Security Baseline
- Enforce HTTPS only
- Restrict SSH via allow-list or VPN
- Store secrets in env files with limited filesystem permissions
- Rotate database password on fixed cadence
- Enable host firewall and fail2ban

## 10. CI/CD (No Paid Services Required)
- Option A: GitHub Actions free minutes (if sufficient)
- Option B: Self-hosted runner in client network

Deployment pattern:
- Build images on approved branch
- Pull images on host
- Compose rolling restart (service by service)

## 11. Acceptance Criteria (Go-Live)
- Core services remain healthy for 48 hours
- API and dashboard available with HTTPS
- Transaction creation + scoring works end to end
- Bulk upload path works with replace mode
- Backup and restore tested once successfully

Go-live sign-off recommendation:
- Require joint sign-off from Fraud Ops lead, IT owner, and Security representative before moving from pilot to business-as-usual operations.

## 12. Risks and Mitigations
- Single-VM risk: use frequent backups and snapshot policy
- Manual ops overhead: automate health checks and patching scripts
- Limited scaling: define scale-out trigger thresholds

## 13. 30/60/90 Day Plan
30 days:
- Stabilize monitoring and runbooks

60 days:
- Introduce staging mirror and release gates

90 days:
- Evaluate migration to managed paid model if scale/compliance grows

## 14. Commercial Positioning Statement
This free-services deployment model is positioned as a high-control, low-license-cost path to production readiness. It is suitable for clients with internal platform capability and a preference for owning operational tooling rather than outsourcing infrastructure management.

---
Prepared for clients requiring free-service deployment with practical production controls.