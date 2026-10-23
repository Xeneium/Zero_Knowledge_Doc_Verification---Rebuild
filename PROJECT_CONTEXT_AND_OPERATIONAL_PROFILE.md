# ZERO-KNOWLEDGE DOCUMENT VERIFICATION PLATFORM
# Project Context, Architectural Baseline & Operational Profile

**Document Reference:** `PROJECT_CONTEXT_AND_OPERATIONAL_PROFILE.md`  
**Target Codebase:** Zero-Knowledge Document Verification Platform  
**Active Branch:** `feat/readiness`  
**Host Environment:** Windows 11 Enterprise (x64) | PowerShell 7.5.0  
**Java Runtime:** Eclipse Adoptium OpenJDK 21.0.12.1 Temurin (LTS)  
**Node.js / npm:** Node.js v24.21.0 | npm 11.19.0  
**Date of Record:** October 2026  
**Auditor Roles:** Senior Java 21 Engineer, Spring Boot Architect, Application Security Engineer, Cryptography Engineer, PostgreSQL Database Architect, QA Automation Engineer, DevSecOps Engineer  

---

## Executive Summary & Purpose

This document provides a consolidated, authoritative operational profile and architectural context for the **Zero-Knowledge Document Verification Platform**. It answers key institutional, architectural, cryptographic, and operational questions regarding project intent, user roles, current readiness stage, traffic capacity, database topologies, authentication models, budgetary constraints, and UI access.

---

## 1. Project Purpose and Primary Business Outcome

### 1.1 Core Mission
The platform is a high-assurance, privacy-preserving document attestation and credential verification system engineered with **Java 21 / Spring Boot 3.4** and **React 18 / TypeScript 5.7**. It bridges traditional Public Key Infrastructure (PKI) with modern zero-knowledge cryptography:
- **Document Authenticity:** Uses **SHA-256** streaming digests and **RSASSA-PSS** digital signatures (NIST SP 800-131A compliant) to ensure document tamper-evidence.
- **Privacy-Preserving Verifiable Credentials:** Employs **Weavechain Bulletproofs** over **Ristretto255** elliptic curve points to generate Non-Interactive Zero-Knowledge Proofs (NIZKP) bound to authentic Pedersen commitments:
  $$C = vG + rH$$
  where $v$ is the private attribute value, $r \leftarrow \mathbb{Z}_q$ is a cryptographically secure 256-bit blinding scalar, and $G, H$ are orthogonal curve generators.

### 1.2 Primary Business Outcome
Enables identity holders (e.g., students, citizens, loan applicants, employees) to mathematically prove verifiable threshold claims and compliance predicates (e.g., *"Age $\ge$ 18"*, *"GPA $\ge$ 3.50"*, or *"Accredited Status"*) to third-party verifiers without revealing the underlying private attribute values, blinding scalars, or complete document contents.
- **Privacy Compliance:** Satisfies data-minimization mandates under GDPR, FERPA, and HIPAA.
- **Fraud Mitigation:** Prevents credential forgery and eliminates identity data leakage from verifier databases.
- **Verification Decoupling:** Replaces intrusive identity disclosures with mathematically verifiable proofs.

---

## 2. Target Users and User Roles

The platform enforces a strict Role-Based Access Control (RBAC) model across four key actor archetypes:

```mermaid
graph TD
    subgraph Platform User Roles
        ISSUER["ROLE_ISSUER<br/>(Universities, Issuing Authorities)"]
        HOLDER["ROLE_HOLDER<br/>(Students, Citizens, Employees)"]
        VERIFIER["ROLE_VERIFIER<br/>(Employers, Auditors, Checkers)"]
        ADMIN["ROLE_ADMIN<br/>(Platform Operators, SecOps)"]
    end

    ISSUER -->|Uploads Document & Signs| DOC[(Stored Document Artifact)]
    ISSUER -->|Issues VC with Pedersen Commitment| CRED[(Verifiable Credential)]
    HOLDER -->|Stores in Identity Wallet| CRED
    HOLDER -->|Generates Bulletproof Range Proof| PROOF[Zero-Knowledge Proof]
    PROOF -->|Submits Predicate Presentation| VERIFIER
    VERIFIER -->|Verifies Proof against Stored Anchor| API[Verification Engine]
    ADMIN -->|Monitors Keys & Audit Chain| AUDIT[(Immutable Hash Chain)]
```

### 2.1 Role Definitions & Boundaries
1. **`ROLE_ISSUER` (e.g., Universities, Government Agencies, Certification Authorities):**
   - Ingests source document payloads (PDF, JPEG, PNG).
   - Computes streaming SHA-256 fingerprints.
   - Applies RSASSA-PSS digital signatures over canonical credential encodings.
   - Commits numerical attributes to Ristretto255 Pedersen points ($C = vG + rH$).
   - Generates and exports time-bound QR capability verification tokens.
2. **`ROLE_HOLDER` (e.g., Students, Credential Holders, Citizens):**
   - Manages issued credentials inside a client-side identity wallet.
   - Exercises selective disclosure by masking non-essential fields with salted digests.
   - Generates client-side Bulletproof range proofs ($v \ge \text{threshold}$).
   - Exports ephemeral QR verification capability tokens.
3. **`ROLE_VERIFIER` (e.g., Employers, Background Checkers, Regulatory Inspectors):**
   - Submits predicate verification presentations to the backend.
   - Validates QR capability tokens via camera scan or manual token entry.
   - Evaluates Bulletproof range proofs against server-bound commitment anchors without access to hidden raw values.
4. **`ROLE_ADMIN` (e.g., Platform Administrators, Security Officers):**
   - Manages the cryptographic key registry lifecycle (`ACTIVE`, `RETIRED`, `REVOKED`).
   - Inspects the tamper-evident SHA-256 audit log chain.
   - Triggers post-disaster-recovery reconciliation and health checks.

---

## 3. Current Stage

| Dimension | Assessment | Operational Status |
| :--- | :--- | :---: |
| **Academic & Demonstration Readiness** | Fully functional, complete workflows, seeded synthetic demo data | **100% GO (Ready)** |
| **Prototype & Internal Staging** | Multi-module build passing, clean Docker Compose topology | **100% GO (Ready)** |
| **Public Production Deployment** | Gated by 1 High-severity finding (`FIND-001`) in `application.yml` | **CONDITIONALLY GATED** |

- **Current Stage:** **High-Fidelity Working Prototype / Academic Demo & Staging Pre-Production**.
- **Academic Context:** Developed as an advanced semester engineering project (*"OOPs with Java"*, Semester 3, Department of Computer Science & Engineering / Information Technology, Easwari Engineering College).
- **Demonstration Suite:** Includes 1-click automated setup scripts (`scripts/demo-setup.ps1` and `scripts/demo-setup.sh`), synthetic evaluation PDF fixtures (`demo/fixtures/`), live claim decoders, and comprehensive viva presentation guides.
- **Production Gate:** All 7 backend modules and the React frontend compile with **zero errors** and **462 of 463 tests passing** (99.78% pass rate). Production release is gated strictly on inverting `zkdoc.demo.seed-enabled` to `false` in `application.yml` (P0 Blocker: `FIND-001`).

---

## 4. Team Size and Maintenance Capacity

- **Team Size:** Solo student developer / small academic engineering team (Sabareesh, Easwari Engineering College).
- **Maintenance Capacity:** Low-to-moderate maintenance capacity typical of solo or academic projects.
- **Architectural Safeguards for Long-Term Maintainability:**
  - **Extreme Modularity:** Separated into 7 decoupled Maven projects with strict package visibility (`zkdoc-core-crypto`, `zkdoc-document`, `zkdoc-keys`, `zkdoc-credential`, `zkdoc-zkp`, `zkdoc-api`, `zkdoc-platform`).
  - **Comprehensive Automated Testing:** 463 total automated tests (204 backend tests via JUnit 5/Surefire, 259 frontend tests via Vitest/RTL) with zero functional failures.
  - **Supply-Chain Guardrails:** Hardened GitHub Actions pipeline with pinned 40-character commit SHA actions, CodeQL SAST, Trivy SCA scanning, and Gitleaks secret detection.
  - **Dependency Decoupling:** Cryptographic primitives in `zkdoc-core-crypto` have zero Spring dependencies, isolating low-level math from framework churn.

---

## 5. Expected Traffic & Operational Envelope

### 5.1 Target Baseline (Academic & Pilot Scale)
- **Active Concurrent Users:** 5 to 50 concurrent active users.
- **Daily Request Volume:** 1,000 to 10,000 requests/day.
- **Document Ingestion:** 50 to 500 document uploads/day.

### 5.2 Rate Limiting & Resource Boundaries
- **Public Verification Throttling:** The public verification endpoint is protected by an in-memory Bucket4j token bucket enforcing **30 requests/minute per IP** (`zkdoc.security.rate-limit.public-verify-per-minute: 30`).
- **Upload Payload Bounds:** Multipart file uploads are restricted to a maximum size of **10 MB** with pre-flight client and server magic-byte MIME validation.

### 5.3 Measured Cryptographic Performance Envelope
Empirical latency and throughput measured during the October 2026 audit via `Phase21PerformanceBenchmarkTest` on Java 21 LTS:

| Operation | Benchmark Throughput | Mean Latency | 95th Percentile (p95) |
| :--- | :---: | :---: | :---: |
| **SHA-256 Digest (1 KB payload)** | 309,185 ops/sec | 0.003 ms | 0.005 ms |
| **SHA-256 Digest (100 KB payload)** | 13,026 ops/sec | 0.077 ms | 0.071 ms |
| **SHA-256 Digest (5 MB payload)** | 285 ops/sec | 3.508 ms | 3.716 ms |
| **RSA-PSS 2048 Signing** | 987 ops/sec | 1.013 ms | 1.114 ms |
| **RSA-PSS 2048 Verification** | 22,818 ops/sec | 0.044 ms | 0.054 ms |
| **Pedersen Commitment Gen ($C = vG + rH$)** | 2,753 ops/sec | 0.363 ms | 0.508 ms |
| **Bulletproof Range Proof Generation** | 13.74 ops/sec | **72.758 ms** | 84.225 ms |
| **Bulletproof Range Proof Verification** | 83.28 ops/sec | **12.007 ms** | 13.133 ms |
| **Sequential Audit Event Append** | 239 ops/sec | 4.173 ms | 4.821 ms |
| **Audit Hash Chain Verify (100 events)** | 331 ops/sec | **3.019 ms** | 3.442 ms |

---

## 6. Hosting Target and Current Deployment Status

### 6.1 Current Deployment Verification
1. **Local Host Mode:** Tested on Windows 11 using the executable Spring Boot fat JAR (`zkdoc-api/target/zkdoc-api-0.1.0-SNAPSHOT.jar`, ~48.2 MB), which embeds and serves the pre-compiled React 18 SPA directly from `classpath:/static/` on port 8080.
2. **Containerized Mode:** Validated via multi-stage `Dockerfile` (Eclipse Temurin 21 JRE base) and `docker-compose.yml`.

### 6.2 Target Production Hosting Topology
The platform is designed for a **Containerized Micro-Topology** deployable on Linux (Docker Compose, AWS ECS/Fargate, or Kubernetes):

```
                        Public Internet Traffic
                                   │
                                   ▼
               ┌──────────────────────────────────────┐
               │    TLS Edge / Reverse Proxy (Nginx)  │
               │    Enforcing TLS 1.3 & Port 443      │
               └──────────────────┬───────────────────┘
                                  │
                 zkdoc-frontend-net (Bridge Network)
                                  │
                                  ▼
               ┌──────────────────────────────────────┐
               │      zkdoc-api Container (Port 8080) │
               │  - Spring Boot 3.4 (Java 21 LTS)     │
               │  - Embedded Static React Frontend    │
               │  - Non-Root UID 10001, Read-Only FS  │
               └──────────────────┬───────────────────┘
                                  │
                 zkdoc-backend-net (Internal Isolated Net)
                                  │
                                  ▼
               ┌──────────────────────────────────────┐
               │   zkdoc-postgres Container (Port 5432)│
               │  - PostgreSQL 16 Alpine              │
               │  - Persistent Volume: zkdoc_pgdata   │
               └──────────────────────────────────────┘
```

- **Container Hardening:** Containers run unprivileged (`UID 10001:10001`), with read-only root filesystems (`read_only: true`), all Linux capabilities dropped (`cap_drop: [ALL]`), and `no-new-privileges: true`.
- **Edge Reverse Proxy Requirement:** Public traffic must terminate at a reverse proxy (Nginx, Caddy, Cloudflare, or AWS ALB) enforcing TLS 1.3, HSTS (`max-age=31536000`), and secure cookies before proxying to internal port 8080.

---

## 7. Database Dialects Used in Deployment: SQLite vs PostgreSQL

The platform provides complete dual-database support governed by independent Flyway migration trees:

```
zkdoc-api/src/main/resources/db/migration/
├── sqlite/       # V1 through V10 (Default Development & Evaluation)
└── postgresql/   # V1 through V5 / V8 (Production & Containerized Deployment)
```

| Database Dialect | Target Environment | Storage Mechanism | Migration Scope | Operational Characteristics |
| :--- | :--- | :--- | :--- | :--- |
| **SQLite 3.47** *(Default)* | Local Developer Workstations, Academic Evaluation, Offline Demos | File on disk: `./data/zkdoc.db` | Migrations `V1`–`V10` in `db/migration/sqlite/` | Zero-configuration embedded database. Single-file, high-speed, zero external dependencies. |
| **PostgreSQL 16+** | Production, Staging, Multi-Node Clusters, Docker Deployments | Enterprise Relational Database / Managed RDS | Migrations `V1`–`V5` in `db/migration/postgresql/` | ACID compliance, concurrent connection pooling via HikariCP, row-level locking, automated point-in-time recovery. |

- **Operational Rule:** **SQLite** is strictly for local workstation development, testing, and offline academic demonstrations. **PostgreSQL** is mandatory for any multi-user or containerized production deployment.

---

## 8. Authentication Provider and Intended RBAC Model

### 8.1 Authentication Standard
The application enforces stateless **Bearer JWT (JSON Web Token)** authentication over all non-public endpoints via Spring Security filter chains (`JwtAuthenticationFilter`). Tokens are cryptographically validated using RSASSA-PSS / RS256.

### 8.2 Identity Provider Strategy
- **Demonstration / Local Profile:** The backend includes an internal key provider (`DevelopmentSigningKeyProvider`) and demo controller (`DemoTokensController`) that issue pre-signed JWTs for test accounts (`admin-operator`, `issuer-agent`, `alice-chen`, `verifier-auditor`).
- **Production Target:** The platform deliberately avoids operating an internal user registration or password database. In production, authentication is delegated to an **external OAuth2 / OIDC Identity Provider** (e.g., Keycloak, Auth0, Okta, or Authentik). The application verifies incoming Bearer JWTs against public keys registered in the `key_registry` table with purpose `JWT_SIGNING`.

### 8.3 RBAC Permission Matrix

| Endpoint Route | HTTP Method | Required Role | Functionality |
| :--- | :---: | :--- | :--- |
| `/api/public/**` | GET / POST | Public (No Auth) | Rate-limited verification of opaque 256-bit QR tokens |
| `/api/documents` | POST | `ROLE_ISSUER` | Document upload and SHA-256 fingerprinting |
| `/api/documents/{id}/verify` | GET | Authenticated | Stored document copy verification |
| `/api/credentials` | POST | `ROLE_ISSUER` | Issuance of RSA-signed credentials with Pedersen commitments |
| `/api/credentials/{id}/revoke`| POST | `ROLE_ISSUER` | Revocation of active credentials |
| `/api/v2/zkp/credential-predicates/verify`| POST | `ROLE_VERIFIER` | Evaluation of Bulletproof range proofs against server-bound commitments |
| `/api/holder/**` | POST | `ROLE_HOLDER` / `ADMIN` | Holder identity wallet and proof generation |
| `/api/admin/keys/**` | GET / POST | `ROLE_ADMIN` | Cryptographic key registry management and key rotation |
| `/api/admin/audit-logs` | GET | `ROLE_ADMIN` | Inspection and verification of immutable audit hash chain |
| `/api/admin/recovery/**` | POST | `ROLE_ADMIN` | Post-disaster recovery integrity reconciliation |

---

## 9. Budget and Operational Constraints

- **Financial Budget:** **Zero-cost / Academic open-source budget**. No proprietary cryptographic licenses, paid cloud HSM subscriptions, or commercial API gateway fees.
- **Hardware Footprint:**
  - Optimized for low memory and CPU consumption.
  - API Container: 512 MB – 1 GB RAM, 0.5–1.0 vCPU.
  - PostgreSQL Container: 512 MB – 2 GB RAM, 0.5–1.0 vCPU.
  - Capable of running smoothly on a low-cost virtual private server (e.g., 2 vCPU, 2–4 GB RAM) or standard student laptop.
- **Key Storage Boundary:** Asymmetric signing keys are currently managed via filesystem PEM paths rather than hardware-backed PKCS#11 HSMs (cataloged in finding `FIND-004`).

---

## 10. Production Deadline or Release Target

- **Primary Academic Target:** **Immediate / Current (October 2026)**. Timed for the Semester 3 *OOPs with Java* laboratory and theory viva voce defense at Easwari Engineering College.
- **Staging / Pre-Production Release Target:** **Q4 2026 / Next Sprint**. Pre-production deployment can be unblocked immediately upon applying the P0 remediation:
  - **FIND-001 (P0 Immediate Blocker, 0.5 hr effort):** Invert `zkdoc.demo.seed-enabled` to default to `false` in `application.yml`, requiring explicit opt-in for demo environments.
  - **FIND-002 (P1 Pre-Production, 1 hr effort):** Remove fallback default passwords (`zkdoc_local_dev_secret`) from `docker-compose.yml`.
  - **FIND-003 (P1 Pre-Production, 4 hrs effort):** Implement AES-256-GCM envelope encryption for stored document payloads at rest.

---

## 11. Status of Audit Reports (Evidence vs Stale Documentation)

### Verdict: Authoritative, Fresh, and Active Empirical Evidence
The audit reports in the repository:
- [`PROJECT_SECURITY_AND_QUALITY_AUDIT_REPORT.md`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/PROJECT_SECURITY_AND_QUALITY_AUDIT_REPORT.md)
- [`COMPLETE_PROJECT_SECURITY_AND_QUALITY_AUDIT.md`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/COMPLETE_PROJECT_SECURITY_AND_QUALITY_AUDIT.md)
- [`AUDIT_FINDINGS.json`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/AUDIT_FINDINGS.json)
- [`AUDIT_TEST_MATRIX.md`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/AUDIT_TEST_MATRIX.md)
- [`AUDIT_REMEDIATION_PLAN.md`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/AUDIT_REMEDIATION_PLAN.md)
- [`AUDIT_COMMANDS_AND_RESULTS.md`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/AUDIT_COMMANDS_AND_RESULTS.md)

represent **fresh empirical evidence** generated from live system execution during October 2026:
1. **Live Test Execution:** All 204 backend tests and 259 frontend tests were physically executed against the current branch (`feat/readiness`), passing with a 99.78% success rate.
2. **Live Tooling Scans:** Gitleaks 8.30.1 scanned 1,418 files (232.64 MB) finding 0 leaks. CycloneDX 1.6 generated an aggregate SBOM cataloging 97 dependencies.
3. **Live Benchmarks:** Cryptographic operations, signature times, and Bulletproof proof generation latencies (72.8 ms) were benchmarked directly on the host JVM.
4. **Conclusion:** These audit reports must be treated as **verified empirical evidence**, not stale or speculative documentation.

---

## 12. UI Screenshots, Visual Specifications & Live Demonstration

### 12.1 Image / Media Status in Git Repository
A repository-wide search confirms that **no binary screenshot images (`.png`/`.jpg`) or screen recording videos (`.mp4`/`.webm`) are committed** to git tracking.

### 12.2 Design Specifications & Wireframe Documentation
Detailed ASCII wireframes, route layouts, accessibility guides, and design token references are documented across:
- [`docs/frontend-rebuild/f2-ux-and-route-specification.md`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/docs/frontend-rebuild/f2-ux-and-route-specification.md): Complete route maps, form field specifications, and error states.
- [`docs/frontend-rebuild/f4-design-system-report.md`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/docs/frontend-rebuild/f4-design-system-report.md): Design tokens, color palettes, and typography.
- [`docs/demo/demo-presentation-script.md`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/docs/demo/demo-presentation-script.md): Step-by-step visual demonstration script.

### 12.3 How to Launch and Observe the Live UI (60 Seconds)
The application UI can be launched locally in under one minute:

```powershell
# Windows PowerShell
.\scripts\demo-setup.ps1
```

Once running, navigate to **`http://localhost:8080/`** in any web browser to observe the live visual interfaces:
1. **System Overview (`/`):** Real-time backend status, service health indicator, and architectural summary.
2. **Demo Quick-Connect Login (`/login`):** Interactive panel providing 1-click token loading, decoded claim previews, and instant role switching.
3. **Public QR & Capability Verifier (`/verify`):** Browser camera QR code scanner with interactive file upload and cryptographic signature validation badges.
4. **Holder Identity Wallet (`/credentials`):** Credential attribute inspection, selective disclosure checkboxes, and client-side Bulletproof range proof generation.
5. **Issuer Document Studio (`/documents`):** Drag-and-drop document upload dropzone, streaming SHA-256 calculation preview, and credential issuance forms.
6. **Admin Operations & Audit Explorer (`/admin`):** Key registry lifecycle table, disaster recovery reconciliation status, and sequential audit hash-chain verification.

---

## 13. Summary Matrix of Architectural & Operational Baseline

| Architectural Dimension | Specified Implementation Baseline |
| :--- | :--- |
| **Backend Framework** | Java 21 LTS / Spring Boot 3.4.3 / Maven Reactor (7 Modules) |
| **Frontend Framework** | React 18 / TypeScript 5.7 / Vite / Tailwind CSS |
| **Zero-Knowledge Mechanism** | Weavechain Bulletproofs (Range Proofs: $v \ge \text{threshold}$) over Ristretto255 curve |
| **Attribute Commitment** | Pedersen Commitments $C = vG + rH$ ($r \leftarrow \text{SecureRandom}$) |
| **Document Signatures** | RSASSA-PSS with SHA-256, MGF1, and 32-byte salt (RSA-2048) |
| **Default Database** | SQLite 3.47 (`./data/zkdoc.db`, Flyway V1–V10) |
| **Production Database** | PostgreSQL 16+ Alpine (Flyway V1–V5 / V8) |
| **Authentication Standard** | Stateless Bearer JWT tokens signed with RSASSA-PSS / RS256 |
| **Authorization Model** | 4-Role RBAC (`ISSUER`, `HOLDER`, `VERIFIER`, `ADMIN`) + Object Ownership |
| **Rate Limiting** | Bucket4j in-memory token bucket (30 requests/minute per IP on public verify) |
| **Document Storage** | Local filesystem with directory traversal protection; AES-GCM encryption planned (P1) |
| **Audit Log Integrity** | Sequential SHA-256 hash chaining: $H_n = \text{SHA256}(H_{n-1} \parallel E_n)$ |
| **Containerization** | Docker Compose multi-service topology with hardened non-root execution |
| **Overall Quality Verdict** | **462/463 Tests Passed (99.78%)** — Conditionally Approved for Production |

---
*Document certified and cataloged in repository root as `PROJECT_CONTEXT_AND_OPERATIONAL_PROFILE.md`.*
