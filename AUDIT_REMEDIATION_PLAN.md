# ZERO-KNOWLEDGE DOCUMENT VERIFICATION PLATFORM
## Technical Remediation Plan & Security Hardening Roadmap

**Document Reference:** `AUDIT_REMEDIATION_PLAN.md`  
**Date:** 2026-10-16  
**Auditor:** Senior Java 21 / AppSec / Cryptography Audit Agent (Google Antigravity)  
**Target Codebase:** Zero-Knowledge Document Verification Platform  
**Target Branch:** `feat/readiness`  

---

## 1. Executive Remediation Summary

This remediation plan provides concrete, code-level engineering instructions to address all 8 security, architectural, and operational findings identified during the October 2026 audit. 

Each finding is categorized into a strict release-gating phase:
- **Phase P0 (Immediate / Release Blocker):** Must be resolved before any deployment outside local development.
- **Phase P1 (High Priority / Pre-Production):** Mandatory hardening prior to staging or production launch.
- **Phase P2 (Medium Priority / Next Release Cycle):** Architectural improvements for scalability and operational resilience.
- **Phase P3 (Low Priority / Technical Debt & Documentation):** Long-term defense-in-depth and specification clarity.

### Remediation Priority Overview

| Finding ID | Severity | Phase | Component | Title | Estimated Effort |
| :---: | :---: | :---: | :--- | :--- | :---: |
| **FIND-001** | **HIGH** | **P0** | `zkdoc-api` | Public Demo Tokens Endpoint Enabled by Default | 0.5 hours |
| **FIND-002** | **MEDIUM** | **P1** | `docker-compose` | Default Static Database Credentials in Docker Manifest | 1.0 hour |
| **FIND-003** | **MEDIUM** | **P1** | `zkdoc-document` | Document Artifact Storage Plaintext Payloads at Rest | 4.0 hours |
| **FIND-004** | **LOW** | **P2** | `zkdoc-keys` | In-Process Asymmetric Keygen & Non-HSM Storage | 8.0 hours |
| **FIND-005** | **LOW** | **P2** | `zkdoc-api` | PostgreSQL Flyway Test Skipped on Offline Docker | 2.0 hours |
| **FIND-006** | **LOW** | **P2** | `zkdoc-api` | In-Memory Token Bucket Limits Multiply in Cluster | 4.0 hours |
| **FIND-007** | **INFO** | **P3** | `zkdoc-zkp` | ZKP Predicate Scope Strictly Limited to Range Inequalities | 1.0 hour |
| **FIND-008** | **LOW** | **P3** | `zkdoc-api` | Relaxed CSP Allows Inline Styles (`'unsafe-inline'`) | 2.0 hours |

---

## 2. Phase P0: Immediate Release Blockers

### FIND-001: Unauthenticated Public Demo Tokens Endpoint Enabled by Default

- **Severity:** HIGH (CVSS 4.0: 8.7 — `CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:N/SC:N/SI:N/SA:N`)
- **CWE:** CWE-1188 (Insecure Default Initialization of Resource)
- **OWASP:** A01:2021-Broken Access Control
- **Affected File:** `zkdoc-api/src/main/resources/application.yml` (Line 50)
- **Related Class:** `zkdoc-api/src/main/java/com/zkdoc/api/demo/DemoTokensController.java`

#### Root Cause
In `application.yml`, the property `zkdoc.demo.seed-enabled` is initialized to default to `true` when the environment variable `ZKDOC_DEMO_SEED_ENABLED` is not provided:
```yaml
zkdoc:
  demo:
    seed-enabled: ${ZKDOC_DEMO_SEED_ENABLED:true}
```
`DemoTokensController` is conditionally loaded when `zkdoc.demo.seed-enabled=true` and registers `GET /api/public/demo-tokens` inside the unauthenticated `/api/public/**` permit-all route. This endpoint dispenses valid RSA-2048-signed JWT bearer tokens for `ADMIN`, `ISSUER`, `VERIFIER`, and `HOLDER`. If deployed to staging or production without explicitly overriding this environment variable, unauthorized remote users can obtain administrative credentials.

#### Step-by-Step Remediation Instructions
1. **Invert Default in `application.yml`**:
   Change the default fallback value in `zkdoc-api/src/main/resources/application.yml` from `true` to `false`:
   ```yaml
   zkdoc:
     demo:
       seed-enabled: ${ZKDOC_DEMO_SEED_ENABLED:false}
   ```
2. **Explicitly Enable in Dev Profile Only**:
   In `zkdoc-api/src/main/resources/application-dev.yml` (or local profile), explicitly declare:
   ```yaml
   zkdoc:
     demo:
       seed-enabled: true
   ```
3. **Add Startup Safety Assertion in Production Profile**:
   In `DemoTokensController` or a configuration validator, add a check that aborts context startup if the active Spring profile is `prod` while `seed-enabled` is true:
   ```java
   @PostConstruct
   public void validateProductionSafety() {
       if (Arrays.asList(environment.getActiveProfiles()).contains("prod") && seedEnabled) {
           throw new IllegalStateException("CRITICAL SECURITY VIOLATION: zkdoc.demo.seed-enabled cannot be true in 'prod' profile!");
       }
   }
   ```

#### Verification & Regression Testing
- Run `mvn test -Dtest=DemoTokensControllerTest`.
- Set `ZKDOC_DEMO_SEED_ENABLED=""` and verify that calling `GET /api/public/demo-tokens` returns HTTP 404 (bean not registered).
- Verify that `scripts/demo-setup.ps1` and `scripts/demo-setup.sh` explicitly pass `ZKDOC_DEMO_SEED_ENABLED=true` when initializing demonstration environments.

---

## 3. Phase P1: High Priority (Pre-Production Hardening)

### FIND-002: Default Static Database Credentials in Docker Compose Manifest

- **Severity:** MEDIUM (CVSS 4.0: 6.9 — `CVSS:4.0/AV:N/AC:L/AT:P/PR:N/UI:N/VC:L/VI:L/VA:N/SC:N/SI:N/SA:N`)
- **CWE:** CWE-798 (Use of Hard-coded Credentials)
- **OWASP:** A07:2021-Identification and Authentication Failures
- **Affected File:** `docker-compose.yml` (Lines 11, 41, 62)

#### Root Cause
`docker-compose.yml` provides static fallback defaults for passwords:
```yaml
POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-zkdoc_local_dev_secret}
```
If a developer or operator executes `docker compose up` without creating a `.env` file containing strong secrets, PostgreSQL and the application container initialize with the well-known secret `zkdoc_local_dev_secret`.

#### Step-by-Step Remediation Instructions
1. **Remove Hardcoded Fallbacks in `docker-compose.yml`**:
   Replace fallback defaults with mandatory environment variables (Docker Compose will raise a warning/error if unset):
   ```yaml
   POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?Error: POSTGRES_PASSWORD must be set in .env}
   ```
2. **Provide `.env.example` Template**:
   Ensure `.env.example` documents required environment variables with instructions:
   ```dotenv
   # Generate strong random secrets: openssl rand -base64 32
   POSTGRES_DB=zkdoc
   POSTGRES_USER=zkdoc_app
   POSTGRES_PASSWORD=
   JWT_SIGNING_KEY_PATH=
   ```
3. **Pre-flight Check in Container Entrypoint**:
   Add a pre-flight validation in `docker/entrypoint.sh` verifying `POSTGRES_PASSWORD != "zkdoc_local_dev_secret"` and `length(POSTGRES_PASSWORD) >= 16`.

#### Verification & Regression Testing
- Run `docker compose config` with no `.env` present and confirm that compose halts with a descriptive error.
- Create `.env` with a strong secret and verify successful database initialization.

---

### FIND-003: Document Artifact Storage Plaintext Payloads at Rest

- **Severity:** MEDIUM (CVSS 4.0: 6.8 — `CVSS:4.0/AV:L/AC:L/AT:N/PR:L/UI:N/VC:H/VI:N/VA:N/SC:N/SI:N/SA:N`)
- **CWE:** CWE-311 (Missing Encryption of Sensitive Data)
- **OWASP:** A02:2021-Cryptographic Failures
- **Affected File:** `zkdoc-document/src/main/java/com/zkdoc/document/storage/FilesystemDocumentArtifactStorage.java`

#### Root Cause
`FilesystemDocumentArtifactStorage.write()` streams document bytes directly from the HTTP upload request onto disk:
```java
Files.copy(contentStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
```
While the storage path is protected from directory traversal and SHA-256 integrity verification is conducted, the stored file payload remains unencrypted at rest. An adversary with read access to the server volume or backup snapshots can read document contents.

#### Step-by-Step Remediation Instructions
1. **Implement Envelope Encryption Stream**:
   Create `EncryptedDocumentArtifactStorage` decorating `FilesystemDocumentArtifactStorage`.
   Use AES-256-GCM (`CipherOutputStream`) initialized with a per-file random 96-bit IV and a master encryption key configured via environment variable `ZKDOC_STORAGE_ENCRYPTION_KEY`:
   ```java
   byte[] iv = new byte[12];
   secureRandom.nextBytes(iv);
   GCMParameterSpec spec = new GCMParameterSpec(128, iv);
   Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec);
   
   // Write IV header (12 bytes) followed by AES-GCM ciphertext stream
   outputStream.write(iv);
   try (CipherOutputStream cos = new CipherOutputStream(outputStream, cipher)) {
       contentStream.transferTo(cos);
   }
   ```
2. **Transparent Decryption on Retrieval**:
   In `read()`, read the first 12 bytes as IV, initialize `Cipher.DECRYPT_MODE`, and return a `CipherInputStream`.
3. **Retain SHA-256 Checksum on Plaintext**:
   Ensure SHA-256 document hashing continues to be calculated on the *plaintext* stream so that cryptographic document attestations remain mathematically valid across storage representations.

#### Verification & Regression Testing
- Create a unit test `EncryptedDocumentStorageTest`: upload a sample PDF, inspect the raw file on disk, verify it contains high-entropy ciphertext with no readable PDF magic headers (`%PDF-`), and retrieve the file to verify byte-for-byte fidelity with the original.

---

## 4. Phase P2: Medium Priority (Architectural & Operational Polish)

### FIND-004: In-Process Asymmetric Key Generation & Non-HSM Storage

- **Severity:** LOW (CVSS 4.0: 4.1 — `CVSS:4.0/AV:L/AC:H/AT:N/PR:L/UI:N/VC:L/VI:L/VA:N/SC:N/SI:N/SA:N`)
- **CWE:** CWE-320 (Key Management Errors)
- **OWASP:** A02:2021-Cryptographic Failures
- **Affected File:** `zkdoc-keys/src/main/java/com/zkdoc/keys/DevelopmentSigningKeyProvider.java`

#### Remediation Plan
1. For development and local testing, keep `DevelopmentSigningKeyProvider` active.
2. For production environments, create a `KmsSigningKeyProvider` implementing `SigningKeyProvider`:
   - Delegate RSA-PSS signing directly to cloud KMS (AWS KMS `sign`, Azure Key Vault `sign`, or HashiCorp Vault Transit engine).
   - The private key never leaves the secure cryptographic boundary of the HSM.
   - Public keys are cached locally in memory for signature verification operations.

---

### FIND-005: PostgreSQL Flyway Test Skipped on Offline Docker

- **Severity:** LOW (CVSS 4.0: 2.5 — `CVSS:4.0/AV:L/AC:H/AT:P/PR:L/UI:N/VC:N/VI:L/VA:N/SC:N/SI:N/SA:N`)
- **CWE:** CWE-1068 (Inconsistency Between Implementation and Documented Design)
- **OWASP:** A05:2021-Security Misconfiguration
- **Affected File:** `zkdoc-api/src/test/java/com/zkdoc/api/PostgresFlywayMigrationIntegrationTest.java`

#### Remediation Plan
1. Maintain `@EnabledIf("isDockerAvailable")` as the primary gate for Testcontainers.
2. Introduce a secondary test profile using an embedded PostgreSQL binary (such as `zonky/embedded-postgres`) or an external connection string fallback `SPRING_DATASOURCE_URL` so developers without Docker Desktop can run:
   ```powershell
   .\mvnw.cmd test -Dtest=PostgresFlywayMigrationIntegrationTest -Dspring.datasource.url=jdbc:postgresql://localhost:5432/zkdoc_test
   ```
3. Ensure CI/CD pipeline (GitHub Actions) runs Docker daemon and treats this test as a blocking quality gate.

---

### FIND-006: In-Memory Token Bucket Limits Multiply in Cluster

- **Severity:** LOW (CVSS 4.0: 2.3 — `CVSS:4.0/AV:N/AC:H/AT:P/PR:N/UI:N/VC:N/VI:N/VA:L/SC:N/SI:N/SA:N`)
- **CWE:** CWE-770 (Allocation of Resources Without Limits or Throttling)
- **OWASP:** A04:2021-Insecure Design
- **Affected File:** `zkdoc-api/src/main/java/com/zkdoc/api/ratelimit/BucketRateLimitService.java`

#### Remediation Plan
1. Abstract `BucketRateLimitService` behind a `RateLimitProvider` interface.
2. Add `RedisBucketRateLimitService` utilizing `bucket4j-redis` or Lettuce:
   ```java
   @Bean
   @Profile("prod")
   public DistributedProxyManager<String> redisProxyManager(RedisClient redisClient) {
       return LettuceBasedProxyManager.builderFor(redisClient)
           .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
           .build();
   }
   ```
3. Keep the current `ConcurrentHashMap` implementation under `@Profile("dev")` for standalone, zero-dependency local runs.

---

## 5. Phase P3: Documentation & Long-Term Defense-in-Depth

### FIND-007: Zero-Knowledge Predicate Scope Strictly Limited to Range Inequalities

- **Severity:** INFORMATIONAL (Design Scope Documentation)
- **CWE:** CWE-1059 (Incomplete Documentation)
- **Affected File:** `zkdoc-zkp/src/main/java/com/zkdoc/zkp/engine/WeavechainBulletproofPredicateEngine.java`

#### Remediation Plan
1. Add explicit Javadoc and architectural guidance clarifying that the platform supports **Numeric Range Predicates ($v \ge \text{threshold}$)** using Weavechain Bulletproofs over Ristretto255 curve commitments.
2. Update user documentation and academic viva briefing sheets to clearly state that arbitrary arithmetic circuits (e.g., Circom/Groth16/PLONK) are not currently required or implemented.

---

### FIND-008: Relaxed Content Security Policy Allows Inline Styles

- **Severity:** LOW (CVSS 4.0: 2.2 — `CVSS:4.0/AV:N/AC:H/AT:N/PR:N/UI:R/VC:N/VI:L/VA:N/SC:N/SI:N/SA:N`)
- **CWE:** CWE-1021 (Improper Restriction of Rendered UI Layers)
- **Affected File:** `zkdoc-api/src/main/java/com/zkdoc/api/security/SecurityConfiguration.java`

#### Remediation Plan
1. Build frontend CSS into deterministic static files (Vite precompilation already packages Tailwind utility classes into bundled CSS).
2. Configure Spring Security to emit a cryptographically secure random nonce per HTTP request and attach it to inline style/script elements:
   ```java
   .contentSecurityPolicy(csp -> csp.policyDirectives(
       "default-src 'self'; script-src 'self'; style-src 'self' 'nonce-" + nonce + "'; img-src 'self' data:; font-src 'self' data:; connect-src 'self';"
   ))
   ```
3. Remove `'unsafe-inline'` from `style-src` directives once nonce integration is verified.

---

## 6. Implementation Checklist & Governance

| Finding | Target Release | Assignee | Reviewer | Verification Test | Status |
| :---: | :---: | :---: | :---: | :---: | :---: |
| **FIND-001** | Hotfix / Sprint 1 | AppSec Lead | Tech Lead | `DemoTokensControllerTest` | Planned |
| **FIND-002** | Hotfix / Sprint 1 | DevOps Lead | SecOps | `docker compose config` | Planned |
| **FIND-003** | Sprint 2 | Backend Lead | Cryptographer | `EncryptedDocumentStorageTest` | Planned |
| **FIND-004** | Sprint 3 | Platform Eng | Cryptographer | KMS Integration Harness | Backlog |
| **FIND-005** | Sprint 2 | QA Automation | Tech Lead | `PostgresFlywayMigrationIntegrationTest` | Planned |
| **FIND-006** | Sprint 3 | Platform Eng | Architect | Distributed Rate Limit Load Test | Backlog |
| **FIND-007** | Sprint 1 | Tech Writer | Cryptographer | Documentation Review | Complete |
| **FIND-008** | Sprint 2 | Frontend Lead | AppSec Lead | Browser CSP Audit | Planned |
