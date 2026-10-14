# Production Deployment Checklist & Operational Runbook

**Document Version:** 1.0.0  
**Phase:** 24 — Production Readiness Assessment & Final Security Review  
**Target Platform:** Zero-Knowledge Document Verification Platform (`zkdoc-platform`)  
**Deployment Profile:** Docker Compose / Linux Container Host with Upstream Reverse Proxy  
**Operational Status:** CONDITIONAL GO Runbook  

---

## 1. Pre-Flight Environment & Hardware Checks

Before initiating any deployment activities, verify that the host platform meets the minimum system specifications and operational prerequisites.

- [ ] **Hardware Capacity**:
  - [ ] Minimum 2 vCPU cores available (recommended 4 vCPU).
  - [ ] Minimum 4 GB RAM available (minimum 1 GB dedicated container limit, 256 MB reservation).
  - [ ] Minimum 20 GB dedicated NVMe/SSD storage for PostgreSQL WAL and document artifacts storage.
- [ ] **Operating System & Kernel**:
  - [ ] Linux (Ubuntu 22.04 LTS / Debian 12 / RHEL 9) with 64-bit kernel $\ge 5.15$.
  - [ ] Core dumps disabled on the host: `ulimit -c 0` (or `sysctl fs.suid_dumpable=0`).
  - [ ] NTP / Chrony time synchronization active and synchronized within $\pm 100\text{ms}$ (critical for JWT validation and proof replay nonces).
- [ ] **Runtime Dependencies**:
  - [ ] Docker Engine $\ge 24.0.0$ installed with Compose V2 (`docker compose`).
  - [ ] `appuser` UID/GID `10001:10001` configured for container volume ownership.
  - [ ] Port availability: Ensure `127.0.0.1:8080` (or internal network) and `127.0.0.1:5432` are unallocated on the host.

---

## 2. Secrets & Environment Configuration

Production secrets must never be committed to git, stored in unencrypted `.env` files in source repositories, or left in bash history.

- [ ] **Secret Generation**:
  - [ ] Generate high-entropy PostgreSQL password ($\ge 32$ alphanumeric characters):
    ```bash
    export POSTGRES_PASSWORD=$(openssl rand -hex 24)
    ```
  - [ ] Generate JWT signing secret ($\ge 256\text{ bits}$ / $\ge 64$ hex characters):
    ```bash
    export ZKDOC_JWT_SECRET=$(openssl rand -hex 32)
    ```
  - [ ] Generate or inject RSA-PSS 2048-bit issuer signing private key:
    ```bash
    # Verify key structure and file permissions (0400)
    chmod 0400 /etc/zkdoc/secrets/issuer_private_key.pem
    chown 10001:10001 /etc/zkdoc/secrets/issuer_private_key.pem
    ```
  - [ ] Set backup encryption and HMAC keys:
    ```bash
    export BACKUP_ENCRYPTION_KEY=$(openssl rand -hex 32)
    export BACKUP_HMAC_KEY=$(openssl rand -hex 32)
    ```
- [ ] **Environment Verification**:
  - [ ] Confirm `SPRING_PROFILES_ACTIVE=postgres` is set.
  - [ ] Verify `SPRING_DATASOURCE_PASSWORD` references `${POSTGRES_PASSWORD}`.
  - [ ] Ensure `ZKDOC_STORAGE_DIR=/var/lib/zkdoc/artifacts` exists on persistent volume.
  - [ ] Confirm no default developer credentials (`zkdoc_local_dev_secret`, `admin123`) remain in active shell environment.

---

## 3. Database Migrations & Schema Validation

Schema migrations are managed declaratively using Flyway.

- [ ] **Pre-Migration Backup**:
  - [ ] If updating an existing deployment, execute an immediate pre-deployment database dump:
    ```bash
    docker compose run --rm backup
    ```
- [ ] **Flyway Migration Execution**:
  - [ ] PostgreSQL container started and healthy:
    ```bash
    docker compose up -d postgres
    docker compose exec postgres pg_isready -U zkdoc_app -d zkdoc
    ```
  - [ ] Start API container to apply pending Flyway migrations:
    ```bash
    docker compose up -d api
    ```
  - [ ] Inspect startup logs for migration success:
    ```bash
    docker compose logs api | grep "Flyway Community Edition"
    docker compose logs api | grep "Successfully applied"
    ```
- [ ] **Schema Integrity Verification**:
  - [ ] Verify Flyway schema history table:
    ```bash
    docker compose exec postgres psql -U zkdoc_app -d zkdoc -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
    ```
  - [ ] Verify core tables exist: `document_metadata`, `credential_entities`, `trusted_issuers`, `refresh_tokens`, `audit_log_entries`.

---

## 4. Docker Container Startup & Healthcheck Polling

Deploy the hardened multi-container architecture.

- [ ] **Launch Containers**:
  - [ ] Execute Docker Compose startup:
    ```bash
    docker compose up -d --build
    ```
- [ ] **Validate Container Isolation & Security**:
  - [ ] Verify `zkdoc-api` is running with non-root UID 10001:
    ```bash
    docker inspect zkdoc-api --format '{{.Config.User}}'
    # Expected: 10001:10001
    ```
  - [ ] Verify root filesystem is read-only:
    ```bash
    docker inspect zkdoc-api --format '{{.HostConfig.ReadonlyRootfs}}'
    # Expected: true
    ```
  - [ ] Verify Linux capabilities are dropped:
    ```bash
    docker inspect zkdoc-api --format '{{.HostConfig.CapDrop}}'
    # Expected: [ALL]
    ```
  - [ ] Verify security opt:
    ```bash
    docker inspect zkdoc-api --format '{{.HostConfig.SecurityOpt}}'
    # Expected: [no-new-privileges:true]
    ```
- [ ] **Healthcheck Polling**:
  - [ ] Poll until container status reaches `healthy`:
    ```bash
    docker compose ps
    ```
  - [ ] Validate readiness probe directly:
    ```bash
    curl -f http://127.0.0.1:8080/actuator/health/readiness
    # Expected: {"status":"UP"}
    ```

---

## 5. Reverse Proxy TLS 1.3 & Header Validation

The Spring Boot backend relies on an upstream reverse proxy (Nginx, Traefik, Caddy, or Cloud ALB) for TLS termination and HTTP security headers.

- [ ] **Port Binding Enforcement**:
  - [ ] Verify port 8080 is NOT exposed directly on public IP `0.0.0.0`:
    ```bash
    ss -tulpn | grep 8080
    # Must bind to 127.0.0.1:8080 or docker0 bridge only
    ```
- [ ] **TLS Configuration**:
  - [ ] TLS 1.3 enabled (TLS 1.0 and 1.1 strictly disabled; TLS 1.2 modern ciphers only as fallback).
  - [ ] Valid CA-signed certificate (Let's Encrypt or corporate PKI) loaded.
- [ ] **Security Headers Inspection**:
  - [ ] Execute `curl -I https://<production-domain>/api/capabilities` and verify presence of:
    - [ ] `Strict-Transport-Security: max-age=31536000; includeSubDomains; preload`
    - [ ] `X-Content-Type-Options: nosniff`
    - [ ] `X-Frame-Options: DENY`
    - [ ] `Content-Security-Policy: default-src 'self'; frame-ancestors 'none';`
    - [ ] `Cache-Control: no-cache, no-store, must-revalidate` (for authenticated/proof endpoints)

---

## 6. Observability, Health & Metric Verification

Verify operational metrics and logging integrations.

- [ ] **Actuator Endpoints**:
  - [ ] Verify `/actuator/health/liveness` returns HTTP 200 `{"status":"UP"}`.
  - [ ] Verify `/actuator/health/readiness` returns HTTP 200 `{"status":"UP"}`.
  - [ ] Verify `/actuator/prometheus` returns Prometheus format metrics:
    ```bash
    curl -s http://127.0.0.1:8080/actuator/prometheus | grep "zkdoc_"
    ```
- [ ] **Metric Scraper Integration**:
  - [ ] Confirm Prometheus server is scraping `/actuator/prometheus` at 15s interval.
  - [ ] Verify Grafana / alert rules are active for:
    - [ ] `jvm_memory_used_bytes / jvm_memory_max_bytes > 0.85`
    - [ ] `hikaricp_connections_pending > 5`
    - [ ] `http_server_requests_seconds_count{status="500"} > 5` in 1 minute.
- [ ] **Audit Logging Verification**:
  - [ ] Issue an test capability request and verify standard JSON structured log event is emitted with correlation ID (`traceId`).

---

## 7. Backup Script Validation & Test Restore

Operational disaster recovery must be confirmed before accepting production traffic.

- [ ] **Execute Automated Backup**:
  - [ ] Trigger containerized backup:
    ```bash
    docker compose run --rm backup
    ```
  - [ ] Confirm backup archive created in `./backups/zkdoc_backup_<timestamp>.tar.gz`.
  - [ ] Verify SHA-256 integrity checksum file exists alongside archive:
    ```bash
    sha256sum -c ./backups/zkdoc_backup_<timestamp>.tar.gz.sha256
    ```
- [ ] **Automated Restore Drill**:
  - [ ] Run dry-run or staging restore verification:
    ```bash
    bash ./scripts/restore.sh --dry-run ./backups/zkdoc_backup_<timestamp>.tar.gz
    ```
  - [ ] Confirm all tables, commitments, and artifact directories match SHA-256 manifests.
- [ ] **Cron Schedule Registration**:
  - [ ] Ensure host crontab executes backup every hour (or nightly):
    ```cron
    0 * * * * cd /opt/zkdoc && docker compose run --rm backup >> /var/log/zkdoc-backup.log 2>&1
    ```

---

## 8. Post-Deployment Smoke Testing & Rollback Procedures

Perform functional validation before pointing live traffic to the host.

- [ ] **Smoke Tests**:
  - [ ] `GET /api/capabilities`: HTTP 200 with platform capabilities (`sha256-hash`, `rsa-pss-signatures`, `bulletproof-predicates`).
  - [ ] `POST /api/auth/login`: Issue valid JWT access and refresh token.
  - [ ] `POST /api/documents/upload`: Upload test PDF; verify SHA-256 hash returned and document persisted.
  - [ ] `POST /api/credentials/issue`: Issue sample credential; verify attribute commitment generation.
  - [ ] `POST /api/zkp/verify-predicate`: Verify range proof against committed credential.
  - [ ] Rate Limit Verification: Send 61 rapid requests; verify HTTP 429 Too Many Requests is triggered.
- [ ] **Rollback Plan**:
  - [ ] If healthcheck fails or smoke test encounters regressions:
    ```bash
    # 1. Stop current containers
    docker compose down
    # 2. Revert image tag or git revision
    docker compose up -d postgres
    # 3. Restore database snapshot if schema migration occurred
    bash ./scripts/restore.sh --force ./backups/zkdoc_pre_deploy_backup.tar.gz
    # 4. Launch previous stable image
    docker compose up -d api
    # 5. Verify readiness
    curl -f http://127.0.0.1:8080/actuator/health/readiness
    ```

---

## 9. Final Sign-Off for Release

- Deployment Engineer: _______________________ Date: ______________
- SRE / Operations Lead: _____________________ Date: ______________
- Security Sign-off: _________________________ Date: ______________
