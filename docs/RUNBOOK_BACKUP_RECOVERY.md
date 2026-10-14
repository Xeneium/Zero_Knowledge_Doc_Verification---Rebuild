# ZKDoc Platform: Backup, Disaster Recovery & Deployment Hardening Runbook
**Phase 22 Operational Specification & Disaster Recovery Playbook**

---

## 1. Overview & Architecture Summary

The Zero-Knowledge Document Verification Platform manages cryptographic trust assets, verifiable credentials, tamper-evident audit records, and raw document evidence. Protecting and recovering this data requires strict operational discipline to prevent data loss, unauthorized key disclosure, or verification failures.

### Supported Deployment Architecture
- **Application Engine:** Java 21 LTS, Spring Boot 3.4.3 running in hardened, non-root Alpine container (`USER 10001:10001`, `read_only: true`, `cap_drop: [ALL]`).
- **Database Engine:** PostgreSQL 16+ (or SQLite in local development), partitioned schema migrations managed via Flyway.
- **Document & Artifact Storage:** Filesystem-backed streaming storage (`FilesystemDocumentArtifactStorage`) mounted on persistent Docker volume (`zkdoc_artifacts:/var/lib/zkdoc/artifacts`), protected against path traversal and silent bit-rot via SHA-256 integrity digests.
- **Network Segmentation:** Split network topology. External traffic is constrained to `zkdoc-frontend-net`; PostgreSQL database traffic is isolated to internal bridge `zkdoc-backend-net` (`internal: true`).

---

## 2. Recovery Objectives (RPO & RTO)

| Metric | Target | Rationale |
|---|---|---|
| **Recovery Point Objective (RPO)** | **< 1 hour** (target) | Hourly database and artifact snapshot intervals guarantee minimal data loss during catastrophic host failure. |
| **Recovery Time Objective (RTO)** | **< 15 minutes** (target) | Container recreation, database restore from custom format (`pg_dump -Fc`), artifact extraction, and automated reconciliation validation. |
| **Backup Frequency** | Daily full (02:00 UTC) + Hourly incremental | Coordinated backup window for PostgreSQL and document storage directory. |
| **Retention Policy** | 7 daily snapshots + 4 weekly archives | Automatic cleanup preserves at least 1 verified good backup at all times. |

> [!IMPORTANT]
> **Assurance Levels Clarification:**
> 1. **Backup Created:** Files have been written to disk by the backup utility.
> 2. **Backup Integrity Verified:** SHA-256 checksums and HMAC signatures match the manifest.
> 3. **Restore Successfully Executed:** Database schema/data and artifact files loaded into an isolated target.
> 4. **Application Recovery Validated:** Post-recovery reconciliation verified schema, artifact hashes, public key trust, credential signatures, and audit hash chains.
> 5. **Disaster-Recovery Objectives Demonstrated:** Tested failover and restoration under simulated total loss.

---

## 3. Data & Secrets Classification

| Asset | Storage Location | Backup Strategy | Private Secret Policy |
|---|---|---|---|
| **Public Key Registry** | Relational DB (`key_registry`) | Included in DB dump | Authoritative public keys, purposes, and statuses (`ACTIVE`, `RETIRING`, `RETIRED`, `REVOKED`). |
| **Private Signing Keys** | In-memory / Secrets Manager / KMS | **EXCLUDED** | **CRITICAL:** Private keys must NEVER be placed in database or artifact backups. Keys are restored via independent secrets management. |
| **Document Artifacts** | Storage directory (`/var/lib/zkdoc/artifacts`) | Archived via `tar.gz` | Exact binary bytes preserved for SHA-256 matching. Path traversal strictly prevented. |
| **Credentials & Commitments** | Relational DB (`credentials`, `attribute_commitments`) | Included in DB dump | Serialized claims, Pedersen commitments, and issuer RSA-PSS digital signatures. |
| **Security Audit Trail** | Relational DB (`security_audit_events`) | Included in DB dump | Append-only sequence with cryptographic SHA-256 hash chaining. Preserved intact without modifications. |
| **Application Configuration** | Environment variables (`.env`) | Version-controlled templates | Passwords and JWT secrets kept in external secrets store. |

---

## 4. Backup Procedures

### 4.1 On-Demand Containerized Backup (Recommended)
Operators can trigger an immediate consistent backup inside the hardened Docker Compose environment:

```bash
docker compose run --rm backup
```

### 4.2 Manual Linux / POSIX Backup
Execute the POSIX backup script with database credentials:

```bash
export POSTGRES_PASSWORD="secure_db_password"
export BACKUP_ENCRYPTION_KEY="optional_aes_key"
export BACKUP_HMAC_KEY="optional_hmac_key"

./scripts/backup.sh
```

### 4.3 Manual Windows PowerShell Backup
Execute the native PowerShell script:

```powershell
$env:POSTGRES_PASSWORD = "secure_db_password"
.\scripts\backup.ps1 -BackupDir ".\backups" -ArtifactsDir ".\data\artifacts" -DatabaseType "postgresql"
```

### 4.4 Backup Artifacts Produced
A successful backup produces:
1. `zkdoc_backup_<timestamp>.tar.gz` (or `.zip`): Contains `database.dump`, `artifacts.tar.gz`, and `manifest.json`.
2. `zkdoc_backup_<timestamp>.tar.gz.sha256`: SHA-256 checksum of the archive bundle.
3. `manifest.json`: Cryptographic manifest containing timestamps, individual file sizes, and SHA-256 checksums.

---

## 5. Restoration Procedures

### 5.1 Pre-Flight Safety Rules
- **NEVER** run restoration against a production database without `--force-overwrite-target`.
- By default, the restore scripts restore into an isolated database: `zkdoc_restore_test`.
- The restoration script verifies all SHA-256 checksums and HMAC signatures **before** touching the target database or filesystem.

### 5.2 Linux / POSIX Restoration

```bash
# 1. Restore into isolated test database for verification
./scripts/restore.sh ./backups/zkdoc_backup_20261014_120000Z.tar.gz \
    --target-db zkdoc_restore_test \
    --target-artifacts ./data/artifacts_test

# 2. Production emergency recovery (requires explicit force flag)
./scripts/restore.sh ./backups/zkdoc_backup_20261014_120000Z.tar.gz \
    --target-db zkdoc \
    --target-artifacts /var/lib/zkdoc/artifacts \
    --force-overwrite-target
```

### 5.3 Windows PowerShell Restoration

```powershell
# Restore into isolated test target
.\scripts\restore.ps1 -BackupArchive ".\backups\zkdoc_backup_20261014_120000Z.zip" `
    -TargetDb "zkdoc_restore_test" `
    -TargetArtifactsDir ".\data\artifacts_test"
```

---

## 6. Post-Restoration Reconciliation & Verification

After database and artifact recovery, the operator must trigger the cryptographic reconciliation engine to validate system health.

### 6.1 Triggering Automated Reconciliation
Using curl or an API client with an authorized `ROLE_ADMIN` bearer token:

```bash
curl -X POST http://localhost:8080/api/admin/recovery/reconcile \
    -H "Authorization: Bearer <ADMIN_JWT_TOKEN>"
```

### 6.2 Reconciliation Checks Performed
1. **Schema Integrity:** Verifies Flyway migration version and checks that no failed migrations exist.
2. **Artifact SHA-256 Verification:** Queries all records in `document_artifacts` and streams physical files from storage to compute real-time SHA-256 digests. Detects missing or corrupted files.
3. **Public Key Registry Trust Audit:** Validates RSA public key PEM structures, key lengths, and policy conformance. Checks for revoked keys to prevent accidental reactivation.
4. **Credential Cryptographic Signatures:** Verifies that active credentials have valid RSA-PSS signatures verifiable by the restored issuer public keys.
5. **Security Audit Hash Chain:** Verifies the cryptographic integrity of the entire audit hash chain (`previous_event_hash` -> `event_hash`) from Genesis (`000...000`) through the latest event. Detects sequence gaps, deletion, or record modification.

### 6.3 Expected Reconciliation Output
```json
{
  "timestamp": "2026-10-14T14:16:22.235Z",
  "status": "HEALTHY",
  "schemaValid": true,
  "schemaVersion": "V8",
  "totalArtifactsChecked": 142,
  "validArtifacts": 142,
  "missingArtifacts": [],
  "corruptedArtifacts": [],
  "totalKeysChecked": 6,
  "activeKeys": 4,
  "revokedKeys": 2,
  "invalidKeys": [],
  "totalCredentialsChecked": 320,
  "validCredentials": 320,
  "invalidCredentials": [],
  "auditChainValid": true,
  "totalAuditEvents": 1450,
  "auditFailureReason": null,
  "summary": "Schema: VALID (version: V8). Artifacts: 142 checked (142 valid, 0 missing, 0 corrupted). Keys: 6 checked (4 active, 2 revoked, 0 invalid). Credentials: 320 checked (320 valid, 0 invalid). Audit Trail: INTACT (1450 events verified)."
}
```

---

## 7. Disaster Recovery Scenarios & Troubleshooting

### Scenario A: Silent Bit-Rot or Tampered Artifacts
- **Symptom:** `status: CORRUPTED`, `corruptedArtifacts: ["doc-uuid expected=... actual=..."]`.
- **Operator Action:** 
  1. Inspect the corrupted artifact file on disk.
  2. Retrieve the artifact from a prior verified backup archive using `tar -xzf <prior_backup> artifacts/<doc-uuid>`.
  3. Re-run `POST /api/admin/recovery/reconcile`.

### Scenario B: Missing Artifact File
- **Symptom:** `status: WARNING`, `missingArtifacts: ["doc-uuid:artifacts/..."]`.
- **Operator Action:**
  1. Check whether the file was archived in secondary object storage or deleted.
  2. If the document is active, restore the missing file or mark the document record suspended/revoked.

### Scenario C: Audit Chain Tampering or Sequence Break
- **Symptom:** `status: CORRUPTED`, `auditChainValid: false`, `auditFailureReason: "Sequence break detected..."` or `"Integrity hash mismatch..."`.
- **Operator Action:**
  1. This indicates direct database tampering or partial record deletion in `security_audit_events`.
  2. Do NOT truncate or regenerate audit records.
  3. Preserve the corrupted database snapshot as security evidence.
  4. Restore from the most recent known-good backup that has a verified audit chain.

### Scenario D: Stale Public Key or Revocation State Rollback
- **Symptom:** A key revoked after the backup date appears active in the restored database.
- **Operator Action:**
  1. Cross-reference the administrative key revocation registry.
  2. If a key was revoked after the backup timestamp, immediately invoke:
     `POST /api/admin/keys/{keyId}/revoke` with revocation reason `"Post-recovery synchronization: key was revoked after backup timestamp"`.

---

## 8. Deployment Hardening Controls

The container deployment implements multi-layered defense-in-depth:
1. **Unprivileged Execution:** Application runs under non-root UID `10001:10001` (`zkdoc:zkdoc`).
2. **Read-Only Root Filesystem:** Container root filesystem is mounted `read_only: true`. All write operations are strictly confined to tmpfs (`/tmp:size=32M`, `/app/tmp:size=32M`) and persistent volume `/var/lib/zkdoc/artifacts`.
3. **Privilege Escalation Prevention:** `no-new-privileges:true` is enforced across all containers.
4. **Dropped Linux Capabilities:** `cap_drop: [ALL]` drops all kernel capabilities.
5. **Network Isolation:** PostgreSQL is only attached to `zkdoc-backend-net` (`internal: true`). No external port is reachable from outside the host.
6. **Persistent Volume Safeguards:** Storage volumes `zkdoc_pgdata` and `zkdoc_artifacts` remain independent of container lifecycle, surviving container destruction and upgrades.
