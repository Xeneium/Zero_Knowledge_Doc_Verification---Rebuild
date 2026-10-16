# Phase F12: Deployment Readiness and Operational Runbook

**Project:** Zero-Knowledge Document Verification Platform  
**Component:** Full-Stack Application Deployment (`frontend/` + `zkdoc-api` + PostgreSQL)  
**Phase:** F12 (Production Acceptance, Deployment Readiness, and Final Quality Gate)  
**Date:** October 2026  
**Status:** Validated for Containerized and Standalone Deployments  

---

## 1. Supported Deployment Architectures

The platform supports two deployment topologies verified against the repository:

### Architecture A: Containerized Micro-Topology (Recommended)
```
                               Internet / Client Traffic
                                          │
                                          ▼
                      ┌──────────────────────────────────────┐
                      │    TLS Edge / Reverse Proxy (Nginx)  │
                      │    (Port 443 -> Port 8080)           │
                      └──────────────────┬───────────────────┘
                                         │
                        zkdoc-frontend-net (Bridge)
                                         │
                                         ▼
                      ┌──────────────────────────────────────┐
                      │      zkdoc-api Container (Port 8080) │
                      │  - Spring Boot 3.4 (Java 21 LTS)     │
                      │  - Embedded Static React 19 Frontend │
                      │  - Non-Root UID 10001, Read-Only FS  │
                      └──────────────────┬───────────────────┘
                                         │
                        zkdoc-backend-net (Internal Bridge)
                                         │
                                         ▼
                      ┌──────────────────────────────────────┐
                      │   zkdoc-postgres Container           │
                      │  - PostgreSQL 16 Alpine              │
                      │  - Port 5432 (Internal Network Only) │
                      │  - Persistent Volume: zkdoc_pgdata   │
                      └──────────────────────────────────────┘
```

### Architecture B: Standalone Enterprise JVM Service
- **Backend:** Spring Boot executable JAR running on Java 21 LTS (`zkdoc-api-0.1.0-SNAPSHOT.jar`).
- **Frontend Assets:** Bundled inside the JAR at `classpath:/static/` and served directly by Spring MVC with caching and security headers.
- **Database:** Enterprise PostgreSQL 16+ cluster with connection pooling via HikariCP.
- **Artifact Storage:** Encrypted local filesystem path (`/var/lib/zkdoc/artifacts`) or enterprise NFS mount.

---

## 2. Infrastructure & Software Prerequisites

| Component | Minimum Version | Production Recommendation | Verified In Repository |
|---|---|---|---|
| **Java Runtime** | OpenJDK 21 LTS | Eclipse Temurin 21.0.x JRE | Yes (`java 21.0.12.1`) |
| **Node.js** | Node.js v22.12.0 | Node.js v24 LTS (LTS runtime) | Yes (`v24.21.0`, npm `11.19.0`) |
| **Container Engine** | Docker Engine 24+ | Docker Engine 26+ / containerd | Yes (`Dockerfile` multi-stage) |
| **Container Compose** | Docker Compose v2.20+ | Docker Compose v2.26+ | Yes (`docker-compose.yml`) |
| **Relational Database** | PostgreSQL 16+ | Managed PostgreSQL (AWS RDS / Cloud SQL) | Yes (`postgres:16-alpine`) |
| **Edge Reverse Proxy** | Nginx 1.24+ / Caddy 2 | Cloudflare / AWS ALB / Nginx | Documented in Section 7 |
| **Memory / CPU (API)** | 512 MB RAM / 0.5 CPU | 1 GB RAM / 2 CPUs | Configured in Compose |
| **Memory / CPU (DB)** | 512 MB RAM / 0.5 CPU | 2 GB RAM / 2 CPUs | Configured in Compose |

---

## 3. Verified Build & Startup Commands

### 3.1 Clean Production Build (Host System)
```powershell
# 1. Build and verify frontend assets
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test:run
npm --prefix frontend run build

# 2. Package Spring Boot executable JAR containing the static frontend
.\mvnw.cmd clean package -DskipTests

# 3. Verify packaged artifact
Get-FileHash -Path zkdoc-api\target\zkdoc-api-0.1.0-SNAPSHOT.jar -Algorithm SHA256
```

### 3.2 Docker Compose Deployment (Verified Path)
```bash
# 1. Create production environment file from template
cp .env.example .env
chmod 600 .env

# 2. Edit .env with strong production database passwords and secrets
# (Set POSTGRES_PASSWORD, SPRING_DATASOURCE_PASSWORD, etc.)

# 3. Build and launch containers in detached mode
docker compose up -d --build

# 4. Monitor startup and verify database migration
docker compose logs -f api

# 5. Check container health status
docker compose ps
```

### 3.3 Standalone JAR Startup
```bash
export SPRING_PROFILES_ACTIVE=postgres
export SPRING_DATASOURCE_URL=jdbc:postgresql://db.internal:5432/zkdoc
export SPRING_DATASOURCE_USERNAME=zkdoc_app
export SPRING_DATASOURCE_PASSWORD="<STRONG_PASSWORD>"
export ZKDOC_STORAGE_BASE_DIR=/var/lib/zkdoc/artifacts
export SERVER_PORT=8080

java -XX:+UseContainerSupport \
     -XX:MaxRAMPercentage=75.0 \
     -Djava.security.egd=file:/dev/./urandom \
     -jar zkdoc-api/target/zkdoc-api-0.1.0-SNAPSHOT.jar
```

---

## 4. Environment Variable Reference

| Environment Variable | Target Profile | Default (Development) | Production Requirement | Description |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | All | `local` | `postgres` or `prod` | Activates database and Flyway configuration profile. |
| `SPRING_DATASOURCE_URL` | `postgres` | `jdbc:postgresql://localhost:5432/zkdoc` | Explicit JDBC connection URI | PostgreSQL connection URL with host and database name. |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | `zkdoc_app` | Explicit restricted user | Least-privilege PostgreSQL application user. |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | (empty) | Injected via Secret Store | Strong database password (minimum 32 characters). |
| `SERVER_PORT` | All | `8080` | `8080` (Internal) | HTTP server port for Spring Boot. |
| `ZKDOC_PUBLIC_VERIFICATION_BASE_URL` | All | `http://localhost:8080` | `https://verify.yourdomain.com` | External base URL used to construct public verification QR codes. |
| `ZKDOC_STORAGE_TYPE` | All | `filesystem` | `filesystem` | Artifact storage engine. |
| `ZKDOC_STORAGE_BASE_DIR` | All | `./data/artifacts` | `/var/lib/zkdoc/artifacts` | Persistent directory for encrypted document payloads. |
| `HIKARI_MAX_POOL_SIZE` | `postgres` | `10` | `20` - `50` | Maximum active database connections in connection pool. |
| `HIKARI_MIN_IDLE` | `postgres` | `2` | `5` - `10` | Minimum idle database connections. |
| `BACKUP_DIR` | Backup | `./backups` | `/var/backups/zkdoc` | Destination directory for encrypted database backups. |
| `BACKUP_ENCRYPTION_KEY` | Backup | (empty) | Injected via Secret Store | Key for AES-256 backup bundle encryption. |
| `BACKUP_HMAC_KEY` | Backup | (empty) | Injected via Secret Store | Key for HMAC-SHA256 manifest authentication. |

---

## 5. Secret Management & Invariant Policies

1. **Zero Secret Embedding:**
   - No private keys, database passwords, or JWT signing secrets are committed to Git, baked into Docker images, or bundled into frontend JavaScript files.
   - Secret scanning via Gitleaks and `scripts/ci-local-verify.ps1` runs in automated CI pipelines.
2. **Secret Injection in Production:**
   - In Kubernetes: Inject secrets via Kubernetes Secret objects mapped to container environment variables or volume mounts.
   - In Docker Compose: Use Docker Secrets or environment variables loaded from a restricted `.env` file (`chmod 600`).
   - In Cloud Platforms: Utilize AWS Secrets Manager, HashiCorp Vault, or GCP Secret Manager to inject runtime credentials dynamically.
3. **Private Key Non-Export Policy:**
   - Document signing keys and JWT keys are referenced by environment variable paths or KMS keys. The database stores only public keys in the `key_registry` table.

---

## 6. Database Initialization & Migration Procedure

The platform uses Flyway for schema management:
- **Local / SQLite Profile:** Migrations `V1` through `V13` in `classpath:db/migration/sqlite`.
- **Production / PostgreSQL Profile:** Migrations `V1` through `V8` in `classpath:db/migration/postgresql`:
  - `V1__init_schema.sql`: Initial core tables.
  - `V2__canonical_domain_entities.sql`: Domain mapping.
  - `V3__key_registry_lifecycle_and_trust.sql`: Key registry and trust lifecycle.
  - `V4__holder_credentials.sql`: Holder credentials and proving stores.
  - `V5__verification_capabilities.sql`: Verification capabilities (Mode B).
  - `V6__document_ownership.sql`: Document ownership and tenancy controls.
  - `V7__credential_lifecycle.sql`: Credential lifecycle state and history log.
  - `V8__security_audit_events.sql`: Continuous SHA-256 chained security audit log.

### Safe Migration Principles:
- Migrations execute automatically upon Spring Boot startup when `spring.flyway.enabled=true`.
- Schema history is tracked in `flyway_schema_history`.
- Migrations are strictly additive; zero column drops or destructive table resets are permitted in production profiles.

---

## 7. Edge TLS & Reverse Proxy Configuration

Spring Boot runs unprivileged on HTTP port 8080. A production reverse proxy must terminate TLS and enforce HTTP security headers.

### Recommended Nginx Configuration (`/etc/nginx/conf.d/zkdoc.conf`):
```nginx
server {
    listen 80;
    server_name verify.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name verify.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/verify.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/verify.yourdomain.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # Security Headers
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header Permissions-Policy "camera=(self), microphone=(), geolocation=()" always;

    # Client Upload Limit (Matches Spring Boot 25MB bound)
    client_max_body_size 25M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 8. Health Probes & Operational Monitoring

| Endpoint | Accessibility | Purpose | Expected Response |
|---|---|---|---|
| `GET /actuator/health/liveness` | Public | Container orchestrator liveness check | `{"status":"UP"}` (HTTP 200) |
| `GET /actuator/health/readiness` | Public | Container orchestrator traffic readiness check | `{"status":"UP"}` (HTTP 200) |
| `GET /api/health` | Public | Application-level health probe | `{"status":"UP","database":"UP"}` |
| `GET /actuator/metrics` | Authenticated (ADMIN) | Micrometer performance and memory metrics | JVM, CPU, and request metrics |
| `GET /api/admin/recovery/status` | Authenticated (ADMIN) | Platform artifact, key, and audit integrity health | Detailed health report |
| `POST /api/admin/recovery/reconcile` | Authenticated (ADMIN) | Triggers on-demand platform consistency check | Reconciliation summary |

---

## 9. Backup & Disaster Recovery Runbook

### 9.1 Automated Backup Execution
Run on-demand using the Docker Compose backup service profile:
```bash
docker compose run --rm backup
```
Or execute the host backup script:
```powershell
.\scripts\backup.ps1 -BackupDir ".\backups" -RetentionDays 7
```

### 9.2 Post-Recovery Reconciliation
Following any database restore or disaster recovery drill:
1. Start the platform in recovery maintenance mode.
2. Log in as an Administrator (`ROLE_ADMIN`).
3. Invoke `POST /api/admin/recovery/reconcile` via the Administrator Dashboard or curl:
   ```bash
   curl -X POST http://localhost:8080/api/admin/recovery/reconcile \
        -H "Authorization: Bearer <ADMIN_JWT>"
   ```
4. Confirm that:
   - All physical document files match their registered SHA-256 digests.
   - All public key anchors in `key_registry` are valid.
   - All credential signatures verify against active trust anchors.
   - The security audit log SHA-256 hash chain is continuous and unbroker.

---

## 10. Production Deployment Checklist

- [ ] **Infrastructure:** PostgreSQL 16+ instance provisioned and network-accessible.
- [ ] **Secrets:** Strong passwords generated and stored in a secure secret manager.
- [ ] **DNS & TLS:** Domain name configured; TLS certificate installed on edge reverse proxy.
- [ ] **Identity Provider:** Production OAuth2/OIDC provider configured; public verification keys registered in `key_registry`.
- [ ] **Storage:** Persistent storage directory `/var/lib/zkdoc/artifacts` mounted with restrictive permissions (`chown 10001:10001`, `chmod 700`).
- [ ] **Environment:** `.env` or container environment variables populated with production URLs and credentials.
- [ ] **Smoke Test:** Health probes (`/api/health`, `/actuator/health/readiness`) return `UP`.
- [ ] **Functional Verification:** Execute public capability verification and issuer document upload test.
- [ ] **Audit Verification:** Check `/api/admin/audit/events` to ensure audit records are logging correctly.
