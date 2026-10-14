# Security model

## What is protected

- Protected API calls require a Bearer JWT whose `kid` resolves to a registered `JWT_SIGNING` public key. The JWT must be a valid RSA-signed token and pass standard time validation.
- The accepted `roles` claim is limited to `ISSUER`, `HOLDER`, `VERIFIER`, and `ADMIN`.
- Only `ISSUER` can issue documents or credentials, or create a document QR code. Other roles receive `403 Forbidden` for those endpoints.
- `VERIFIER` is required for the credential-predicate verification endpoint. `ADMIN` is required to register keys or change a key's status.
- Verification accepts signatures made by **active** and **retired** registered keys. A **revoked** key is excluded before signature verification and therefore fails verification.
- Every completed verification attempt records the authenticated subject (or `anonymous`), timestamp, target identifier when available, event type, and pass/fail result. The audit table does not store request bodies, uploaded files, hashes, signatures, proof bytes, or private keys.

## Intentionally public

`GET /api/health` and `GET /api/public/verify/{token}` are unauthenticated. The latter is rate limited and returns only signature validity and document activity; it does not disclose document files, paths, identities, or hashes.

## Frontend security boundary

- The browser calls only confirmed API routes. The public verification response is runtime-checked before it can produce a trusted result.
- QR contents are length- and structure-validated, are never executed or opened, and are reduced to an opaque backend token before a same-origin request.
- Camera decoding occurs locally. Access requires an explicit action, and streams are stopped after a scan, cancellation, error, or navigation.
- Verification references and results are held only in component memory; the frontend does not place them in URLs, local storage, or session storage.
- Shared HTTP errors expose only status-specific public messages and constrained error codes. Backend messages and exception details are not propagated to the interface.
- Production source maps are disabled. No analytics, telemetry, authentication tokens, private keys, or document contents are included in the browser bundle.
- The frontend has no authentication provider. Protected API workflows are deliberately non-operational, and the informational operations page contains no protected data or privileged controls.

## Important limitations and operator responsibilities

- **Private keys are environment-injected files, not HSM-backed.** The registry contains public-key metadata and an environment-variable name for a private-key file; it never accepts or returns private-key bytes.
- Current document and credential signing keys are generated in-process at startup by the Phase 1/3 services. They are not yet wired through the key registry and cannot survive a restart. Do not treat this local-development behaviour as production key management.
- JWT issuance is deliberately outside this application. An operator must provision the JWT public key in the registry, securely operate the issuer, and use a unique `kid` for key rotation.
- SQLite is the project's only configured database. It is not encrypted at rest by this application and is suitable only for a carefully secured single-node deployment with restrictive file permissions, controlled backups, and an explicit retention policy.
- JWT issuer and audience restrictions are not configured by default; deployers must add those constraints for their identity provider. HTTPS/TLS termination, secret-file permissions, database backups, log retention, and rate-limit storage are deployment responsibilities.
- The audit log is operational metadata, not a tamper-evident ledger. Restrict database access and retention according to your policy.

---

## Software Supply-Chain & CI/CD Security

### 1. Workflow Least-Privilege Permissions
- Global workflow access is default read-only (`permissions: contents: read`).
- Specific write capabilities (e.g. `security-events: write` for SARIF uploads) are scoped exclusively to the job performing the analysis and denied to generic build/test tasks.
- Untrusted pull requests never receive repository secrets or deployment credentials.

### 2. Action Pinning and Supply-Chain Integrity
- All third-party GitHub Actions are pinned to immutable 40-character commit SHAs with inline version comments to prevent supply-chain attacks via mutable tags.
- Build dependencies are resolved deterministically using Maven reactor coordinates and npm `package-lock.json` with `npm ci`.

### 3. Software Bill of Materials (SBOM) & Provenance
- Machine-readable CycloneDX v1.6 SBOMs are generated for both backend Java components (`bom.json`, `bom.xml` via `cyclonedx-maven-plugin`) and frontend npm packages (`frontend-bom.json` via `npm sbom`).
- Cryptographic SHA-256 digests (`sbom-checksums.sha256`) and application artifact hashes (`zkdoc-api-artifact.sha256`) are computed and retained as build artifacts.

### 4. Vulnerability Management & SCA Policy
- **Blocking Severity**: Any unresolved `CRITICAL` or `HIGH` vulnerabilities in runtime dependencies block the pipeline.
- **Build Tooling vs Runtime**: Build-time devDependencies (e.g., CSS compilers) are evaluated based on exploitability in a non-server context and remediated via overrides where patches exist (e.g., `source-map-js@1.2.2`).
- **Secret Scanning**: Gitleaks executes across the full commit history with `.gitleaks.toml` rules. Legitimate synthetic test keys are strictly confined to dedicated test classes.
- **Container Hardening**: The multi-stage Dockerfile runs unprivileged as `zkdoc:zkdoc` (UID 10001), includes a container health check, minimizes the runtime attack surface via `eclipse-temurin:21-jre-alpine`, drops all Linux capabilities (`cap_drop: [ALL]`), prevents privilege escalation (`no-new-privileges:true`), and enforces a read-only root filesystem (`read_only: true`) with explicit tmpfs mounts.

---

## 💾 Backup, Disaster Recovery & Deployment Hardening

For comprehensive procedures, recovery architectures, and operational drills, refer to [`docs/RUNBOOK_BACKUP_RECOVERY.md`](docs/RUNBOOK_BACKUP_RECOVERY.md).

### 1. Private Key & Proving Secret Isolation Invariant
- **Non-Export Policy**: Private signing keys (document signing, credential issuance, JWT signing) and holder zero-knowledge proving secrets are **strictly forbidden** from database and artifact backup bundles.
- **Automated Manifest Validation**: Automated backup scripts (`scripts/backup.sh` and `scripts/backup.ps1`) actively scan database dumps and configuration files for asymmetric private key headers (`BEGIN PRIVATE KEY`, `BEGIN RSA PRIVATE KEY`) and abort immediately if private key material is detected.
- **Key Recovery Separation**: Public key trust registries are backed up for trust validation; corresponding private keys must be managed through dedicated, out-of-band KMS/secrets infrastructure with independent access policies.

### 2. Backup Integrity & Authenticity
- **Cryptographic Manifests**: Every backup bundle generates a machine-readable `manifest.json` containing SHA-256 checksums of the database dump and all document artifact payloads.
- **Encrypted Archives**: Production backups should be encrypted using AES-256-GCM with PBKDF2/Argon2 key derivation or GPG asymmetric encryption to guarantee confidentiality and tamper-evidence at rest.
- **Restoration Safeguards**: Restoration scripts (`scripts/restore.sh`, `scripts/restore.ps1`) enforce pre-flight SHA-256 manifest verification and require explicit confirmation (`--force-overwrite-target`) before modifying any target datastore or artifact directory.

### 3. Post-Recovery Reconciliation Engine
- **Integrity Validation Endpoint**: The application exposes `POST /api/admin/recovery/reconcile` (restricted to `ROLE_ADMIN`), triggering a multi-layer verification:
  - **Schema Validation**: Validates Flyway migration history consistency.
  - **Artifact SHA-256 Digests**: Streams every physical artifact file from disk and recalculates its SHA-256 digest against the database `document_artifacts.sha256_hex` record.
  - **Key Registry State**: Validates public key PEM formats and active/retired/revoked statuses.
  - **Credential Signatures**: Re-verifies RSA-PSS signatures over canonical credential encodings using registered issuer keys.
  - **Audit Log Chain**: Verifies the continuous SHA-256 hash chain of the `security_audit_events` table (`prev_event_hash -> current_event_hash`).
- **Audit Logging**: Every recovery reconciliation execution emits a structured security audit event (`RECOVERY_RECONCILED` or `RECOVERY_FAILED`).

