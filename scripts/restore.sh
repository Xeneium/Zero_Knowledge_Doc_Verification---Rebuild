#!/usr/bin/env bash
# ==============================================================================
# Zero-Knowledge Document Verification Platform
# Production Restoration Script (PostgreSQL + Artifacts + Validation)
# Phase 22 - Backup, Recovery & Deployment Hardening
# ==============================================================================
set -euo pipefail

# ------------------------------------------------------------------------------
# Usage & Argument Parsing
# ------------------------------------------------------------------------------
usage() {
    echo "Usage: $0 <backup_archive.tar.gz> [options]"
    echo ""
    echo "Options:"
    echo "  --target-db <dbname>              Target database name (Default: zkdoc_restore_test)"
    echo "  --target-artifacts <dir>          Target artifacts directory (Default: ./data/artifacts_restored)"
    echo "  --target-sqlite <path>            Target SQLite database path (for SQLite backups)"
    echo "  --force-overwrite-target          Required if target-db is the production database"
    echo "  --help                            Show this message"
    echo ""
    echo "Example:"
    echo "  $0 ./backups/zkdoc_backup_20261014_120000Z.tar.gz --target-db zkdoc_restore_test"
    exit 1
}

if [[ $# -lt 1 ]]; then
    usage
fi

BACKUP_ARCHIVE="$1"
shift

TARGET_DB="zkdoc_restore_test"
TARGET_ARTIFACTS_DIR="./data/artifacts_restored"
TARGET_SQLITE_PATH="./data/zkdoc_restored.db"
FORCE_OVERWRITE=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --target-db)
            TARGET_DB="$2"
            shift 2
            ;;
        --target-artifacts)
            TARGET_ARTIFACTS_DIR="$2"
            shift 2
            ;;
        --target-sqlite)
            TARGET_SQLITE_PATH="$2"
            shift 2
            ;;
        --force-overwrite-target)
            FORCE_OVERWRITE=true
            shift
            ;;
        --help)
            usage
            ;;
        *)
            echo "[-] Unknown option: $1" >&2
            usage
            ;;
    esac
done

if [[ ! -f "${BACKUP_ARCHIVE}" ]]; then
    echo "[-] Error: Backup archive not found: ${BACKUP_ARCHIVE}" >&2
    exit 1
fi

# ------------------------------------------------------------------------------
# Production Safeguards Check
# ------------------------------------------------------------------------------
PROD_DB_NAME="${POSTGRES_DB:-zkdoc}"
if [[ "${TARGET_DB}" == "${PROD_DB_NAME}" && "${FORCE_OVERWRITE}" != "true" ]]; then
    echo "[-] CRITICAL SAFEGUARD VIOLATION: Target database '${TARGET_DB}' matches production database!" >&2
    echo "[-] Destructive restoration into active production requires explicit '--force-overwrite-target'." >&2
    exit 2
fi

STAGE_DIR="/tmp/zkdoc_restore_$(date +%s)_$$"
mkdir -p "${STAGE_DIR}"

cleanup() {
    rm -rf "${STAGE_DIR}"
}
trap cleanup EXIT

echo "=============================================================================="
echo " Starting ZKDoc Platform Restoration"
echo " Backup Archive:    ${BACKUP_ARCHIVE}"
echo " Target Database:   ${TARGET_DB}"
echo " Target Artifacts:  ${TARGET_ARTIFACTS_DIR}"
echo "=============================================================================="

# ------------------------------------------------------------------------------
# Step 1: Decrypt and Unpack Archive
# ------------------------------------------------------------------------------
echo "[*] Step 1: Unpacking and verifying backup bundle..."
WORKING_ARCHIVE="${BACKUP_ARCHIVE}"

if [[ "${BACKUP_ARCHIVE}" == *.enc ]]; then
    if [[ -z "${BACKUP_ENCRYPTION_KEY:-}" ]]; then
        echo "[-] Error: Encrypted backup requires BACKUP_ENCRYPTION_KEY environment variable." >&2
        exit 1
    fi
    echo "[*] Decrypting AES-256 archive..."
    openssl enc -d -aes-256-cbc -pbkdf2 -in "${BACKUP_ARCHIVE}" -out "${STAGE_DIR}/decrypted.tar.gz" -pass env:BACKUP_ENCRYPTION_KEY
    WORKING_ARCHIVE="${STAGE_DIR}/decrypted.tar.gz"
fi

tar -xzf "${WORKING_ARCHIVE}" -C "${STAGE_DIR}"

# ------------------------------------------------------------------------------
# Step 2: Validate Manifest & Checksums
# ------------------------------------------------------------------------------
echo "[*] Step 2: Verifying cryptographic checksums from manifest..."
if [[ ! -f "${STAGE_DIR}/manifest.json" ]]; then
    echo "[-] Error: Backup manifest.json is missing! Archive may be corrupted or tampered." >&2
    exit 1
fi

cd "${STAGE_DIR}"
if [[ -f "manifest.sha256" ]]; then
    sha256sum -c manifest.sha256
    echo "[+] Manifest SHA-256 signature verified."
fi

# Verify HMAC if key configured
if [[ -n "${BACKUP_HMAC_KEY:-}" && -f "manifest.json.hmac" ]]; then
    EXPECTED_HMAC="$(cat manifest.json.hmac | tr -d ' ')"
    ACTUAL_HMAC="$(openssl dgst -sha256 -hmac "${BACKUP_HMAC_KEY}" manifest.json | awk '{print $2}')"
    if [[ "${EXPECTED_HMAC}" != "${ACTUAL_HMAC}" ]]; then
        echo "[-] CRITICAL SECURITY VIOLATION: Manifest HMAC mismatch! Tampering detected." >&2
        exit 2
    fi
    echo "[+] Manifest HMAC authenticity verified."
fi

DB_TYPE="$(grep '"databaseType"' manifest.json | head -n 1 | cut -d '"' -f 4)"
echo "[+] Detected backup database type: ${DB_TYPE}"

# Verify database file checksum
DB_FILE="$(ls database.* 2>/dev/null | head -n 1)"
DB_EXPECTED_HASH="$(grep -A 3 '"database"' manifest.json | grep '"sha256"' | cut -d '"' -f 4)"
DB_ACTUAL_HASH="$(sha256sum "${DB_FILE}" | awk '{print $1}')"

if [[ "${DB_EXPECTED_HASH}" != "${DB_ACTUAL_HASH}" ]]; then
    echo "[-] CRITICAL CORRUPTION: Database hash mismatch! Expected: ${DB_EXPECTED_HASH}, Actual: ${DB_ACTUAL_HASH}" >&2
    exit 3
fi
echo "[+] Database checksum verified: ${DB_ACTUAL_HASH}"

# Verify artifacts archive checksum
if [[ -f "artifacts.tar.gz" ]]; then
    ART_EXPECTED_HASH="$(grep -A 3 '"artifacts"' manifest.json | grep '"sha256"' | cut -d '"' -f 4)"
    ART_ACTUAL_HASH="$(sha256sum "artifacts.tar.gz" | awk '{print $1}')"
    if [[ "${ART_EXPECTED_HASH}" != "${ART_ACTUAL_HASH}" ]]; then
        echo "[-] CRITICAL CORRUPTION: Artifacts archive hash mismatch! Expected: ${ART_EXPECTED_HASH}, Actual: ${ART_ACTUAL_HASH}" >&2
        exit 3
    fi
    echo "[+] Artifacts archive checksum verified: ${ART_ACTUAL_HASH}"
fi

# ------------------------------------------------------------------------------
# Step 3: Restore Database
# ------------------------------------------------------------------------------
echo "[*] Step 3: Restoring database to target: ${TARGET_DB}..."
if [[ "${DB_TYPE}" == "postgresql" ]]; then
    export PGPASSWORD="${POSTGRES_PASSWORD:-}"
    pg_restore \
        -h "${POSTGRES_HOST:-localhost}" \
        -p "${POSTGRES_PORT:-5432}" \
        -U "${POSTGRES_USER:-zkdoc_app}" \
        -d "${TARGET_DB}" \
        --clean \
        --if-exists \
        --no-owner \
        --no-privileges \
        "${STAGE_DIR}/${DB_FILE}"
    unset PGPASSWORD
    echo "[+] PostgreSQL database restored into '${TARGET_DB}'."
elif [[ "${DB_TYPE}" == "sqlite" ]]; then
    mkdir -p "$(dirname "${TARGET_SQLITE_PATH}")"
    cp "${STAGE_DIR}/${DB_FILE}" "${TARGET_SQLITE_PATH}"
    echo "[+] SQLite database restored to '${TARGET_SQLITE_PATH}'."
fi

# ------------------------------------------------------------------------------
# Step 4: Restore Document Artifacts
# ------------------------------------------------------------------------------
echo "[*] Step 4: Restoring document artifacts to: ${TARGET_ARTIFACTS_DIR}..."
mkdir -p "${TARGET_ARTIFACTS_DIR}"
if [[ -f "${STAGE_DIR}/artifacts.tar.gz" ]]; then
    tar -xzf "${STAGE_DIR}/artifacts.tar.gz" -C "${TARGET_ARTIFACTS_DIR}"
    echo "[+] Document artifacts extracted."
fi

# ------------------------------------------------------------------------------
# Step 5: Post-Restoration Guidance
# ------------------------------------------------------------------------------
echo "=============================================================================="
echo " [+] RESTORATION EXECUTED SUCCESSFULLY"
echo ""
echo " Next Steps for Operator Verification:"
echo " 1. Point the recovery validator at the restored target instance:"
echo "    POST /api/admin/recovery/reconcile"
echo " 2. Verify that schema, artifacts SHA-256 digests, and security audit hash"
echo "    chains report status: HEALTHY."
echo " 3. Perform synthetic credential and signature verification probes."
echo "=============================================================================="
