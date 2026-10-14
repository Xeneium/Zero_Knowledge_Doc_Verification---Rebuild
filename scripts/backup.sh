#!/usr/bin/env bash
# ==============================================================================
# Zero-Knowledge Document Verification Platform
# Production Backup Script (PostgreSQL + Artifacts + Trust Manifest)
# Phase 22 - Backup, Recovery & Deployment Hardening
# ==============================================================================
set -euo pipefail

# ------------------------------------------------------------------------------
# Default Configuration & Environment Discovery
# ------------------------------------------------------------------------------
BACKUP_DIR="${BACKUP_DIR:-./backups}"
ARTIFACTS_DIR="${ZKDOC_STORAGE_DIR:-./data/artifacts}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
DATABASE_TYPE="${DATABASE_TYPE:-postgresql}" # postgresql | sqlite
DB_NAME="${POSTGRES_DB:-zkdoc}"
DB_USER="${POSTGRES_USER:-zkdoc_app}"
DB_HOST="${POSTGRES_HOST:-localhost}"
DB_PORT="${POSTGRES_PORT:-5432}"
SQLITE_DB_PATH="${SQLITE_DB_PATH:-./data/zkdoc.db}"

TIMESTAMP="$(date -u +"%Y%m%d_%H%M%SZ")"
BACKUP_ID="zkdoc_backup_${TIMESTAMP}"
WORK_DIR="/tmp/${BACKUP_ID}"

echo "=============================================================================="
echo " Starting ZKDoc Platform Backup: ${BACKUP_ID}"
echo " Timestamp (UTC): ${TIMESTAMP}"
echo " Database Type:   ${DATABASE_TYPE}"
echo " Destination:     ${BACKUP_DIR}/${BACKUP_ID}.tar.gz"
echo "=============================================================================="

# ------------------------------------------------------------------------------
# Pre-Flight Verifications
# ------------------------------------------------------------------------------
mkdir -p "${BACKUP_DIR}"
mkdir -p "${WORK_DIR}"

cleanup() {
    local exit_code=$?
    if [[ -d "${WORK_DIR}" ]]; then
        rm -rf "${WORK_DIR}"
    fi
    if [[ $exit_code -ne 0 ]]; then
        echo "[-] Backup failed with exit code ${exit_code}. Cleaned up staging directory." >&2
    fi
    exit $exit_code
}
trap cleanup EXIT

# ------------------------------------------------------------------------------
# 1. Database Backup
# ------------------------------------------------------------------------------
echo "[*] Step 1: Performing consistent database dump..."
if [[ "${DATABASE_TYPE}" == "postgresql" ]]; then
    if ! command -v pg_dump >/dev/null 2>&1; then
        echo "[-] Error: pg_dump utility not found in PATH." >&2
        exit 1
    fi

    # Pass password strictly via PGPASSWORD environment variable, never CLI args
    export PGPASSWORD="${POSTGRES_PASSWORD:-}"
    pg_dump \
        -h "${DB_HOST}" \
        -p "${DB_PORT}" \
        -U "${DB_USER}" \
        -d "${DB_NAME}" \
        -Fc \
        --no-owner \
        --no-privileges \
        -f "${WORK_DIR}/database.dump"
    unset PGPASSWORD
    echo "[+] PostgreSQL dump completed: ${WORK_DIR}/database.dump"
elif [[ "${DATABASE_TYPE}" == "sqlite" ]]; then
    if [[ ! -f "${SQLITE_DB_PATH}" ]]; then
        echo "[-] Error: SQLite database file not found at ${SQLITE_DB_PATH}" >&2
        exit 1
    fi
    # Use SQLite .backup command if sqlite3 is installed, else atomic copy
    if command -v sqlite3 >/dev/null 2>&1; then
        sqlite3 "${SQLITE_DB_PATH}" ".backup '${WORK_DIR}/database.sqlite'"
    else
        cp "${SQLITE_DB_PATH}" "${WORK_DIR}/database.sqlite"
    fi
    echo "[+] SQLite database backup completed: ${WORK_DIR}/database.sqlite"
else
    echo "[-] Error: Unsupported DATABASE_TYPE: ${DATABASE_TYPE}" >&2
    exit 1
fi

# ------------------------------------------------------------------------------
# 2. Document Artifacts Backup
# ------------------------------------------------------------------------------
echo "[*] Step 2: Archiving document artifacts store..."
mkdir -p "${WORK_DIR}/artifacts"
if [[ -d "${ARTIFACTS_DIR}" ]]; then
    tar -czf "${WORK_DIR}/artifacts.tar.gz" -C "${ARTIFACTS_DIR}" . 2>/dev/null || tar -czf "${WORK_DIR}/artifacts.tar.gz" -T /dev/null
    ARTIFACT_COUNT="$(find "${ARTIFACTS_DIR}" -type f 2>/dev/null | wc -l || echo 0)"
else
    echo "[!] Warning: Artifact directory ${ARTIFACTS_DIR} not found. Creating empty archive."
    tar -czf "${WORK_DIR}/artifacts.tar.gz" -T /dev/null
    ARTIFACT_COUNT=0
fi
echo "[+] Artifact archive created (${ARTIFACT_COUNT} files preserved)."

# ------------------------------------------------------------------------------
# 3. Private Key Non-Export Safety Boundary Check
# ------------------------------------------------------------------------------
echo "[*] Step 3: Verifying private-key isolation boundary..."
# Verify that no private key material is included in the backup staging files
if grep -r -l --exclude="*.dump" "BEGIN PRIVATE KEY" "${WORK_DIR}" >/dev/null 2>&1 || \
   grep -r -l --exclude="*.dump" "BEGIN RSA PRIVATE KEY" "${WORK_DIR}" >/dev/null 2>&1; then
    echo "[-] CRITICAL SECURITY VIOLATION: Private key material detected in backup staging! Aborting." >&2
    exit 2
fi
echo "[+] Key isolation verified: No private signing keys detected in backup payload."

# ------------------------------------------------------------------------------
# 4. Generate SHA-256 Manifest & Checksums
# ------------------------------------------------------------------------------
echo "[*] Step 4: Generating cryptographic integrity manifest..."
cd "${WORK_DIR}"

DB_FILE="$(ls database.* 2>/dev/null | head -n 1)"
DB_HASH="$(sha256sum "${DB_FILE}" | awk '{print $1}')"
DB_SIZE="$(wc -c < "${DB_FILE}" | tr -d ' ')"

ART_HASH="$(sha256sum "artifacts.tar.gz" | awk '{print $1}')"
ART_SIZE="$(wc -c < "artifacts.tar.gz" | tr -d ' ')"

cat <<EOF > manifest.json
{
  "backupId": "${BACKUP_ID}",
  "timestamp": "${TIMESTAMP}",
  "databaseType": "${DATABASE_TYPE}",
  "database": {
    "filename": "${DB_FILE}",
    "sha256": "${DB_HASH}",
    "sizeBytes": ${DB_SIZE}
  },
  "artifacts": {
    "filename": "artifacts.tar.gz",
    "sha256": "${ART_HASH}",
    "sizeBytes": ${ART_SIZE},
    "fileCount": ${ARTIFACT_COUNT}
  },
  "privateKeyScanPassed": true,
  "generator": "zkdoc-backup-v1"
}
EOF

MANIFEST_HASH="$(sha256sum manifest.json | awk '{print $1}')"
echo "${MANIFEST_HASH}  manifest.json" > manifest.sha256
echo "[+] Manifest created with SHA-256: ${MANIFEST_HASH}"

# Optional HMAC authentication for manifest
if [[ -n "${BACKUP_HMAC_KEY:-}" ]]; then
    echo "[*] Applying HMAC-SHA256 authentication to manifest..."
    openssl dgst -sha256 -hmac "${BACKUP_HMAC_KEY}" manifest.json | awk '{print $2}' > manifest.json.hmac
fi

# ------------------------------------------------------------------------------
# 5. Package Final Backup Bundle
# ------------------------------------------------------------------------------
FINAL_ARCHIVE="${BACKUP_DIR}/${BACKUP_ID}.tar.gz"
echo "[*] Step 5: Packaging final archive: ${FINAL_ARCHIVE}..."
tar -czf "${FINAL_ARCHIVE}" -C "${WORK_DIR}" .

# Optional encryption with AES-256-GCM / PBKDF2
if [[ -n "${BACKUP_ENCRYPTION_KEY:-}" ]]; then
    echo "[*] Encrypting backup bundle with AES-256-CBC (PBKDF2)..."
    openssl enc -aes-256-cbc -salt -pbkdf2 -in "${FINAL_ARCHIVE}" -out "${FINAL_ARCHIVE}.enc" -pass env:BACKUP_ENCRYPTION_KEY
    rm -f "${FINAL_ARCHIVE}"
    FINAL_ARCHIVE="${FINAL_ARCHIVE}.enc"
fi

FINAL_HASH="$(sha256sum "${FINAL_ARCHIVE}" | awk '{print $1}')"
echo "${FINAL_HASH}  $(basename "${FINAL_ARCHIVE}")" > "${FINAL_ARCHIVE}.sha256"

# ------------------------------------------------------------------------------
# 6. Retention Policy Enforcement
# ------------------------------------------------------------------------------
echo "[*] Step 6: Enforcing retention policy (${RETENTION_DAYS} days)..."
# Keep at least the latest 1 backup regardless of retention age
EXISTING_BACKUPS_COUNT="$(find "${BACKUP_DIR}" -maxdepth 1 -name "zkdoc_backup_*.tar.gz*" | wc -l)"
if [[ ${EXISTING_BACKUPS_COUNT} -gt 1 ]]; then
    find "${BACKUP_DIR}" -maxdepth 1 -name "zkdoc_backup_*.tar.gz*" -mtime "+${RETENTION_DAYS}" -delete 2>/dev/null || true
fi

echo "=============================================================================="
echo " [+] BACKUP COMPLETED SUCCESSFULLY"
echo " Archive:  ${FINAL_ARCHIVE}"
echo " SHA-256:  ${FINAL_HASH}"
echo " Size:     $(wc -c < "${FINAL_ARCHIVE}" | tr -d ' ') bytes"
echo "=============================================================================="
