-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V9__holder_credentials.sql (SQLite)
-- Phase 6A: Holder Credential & Proving-Material Provisioning Architecture
-- ==============================================================================

CREATE TABLE IF NOT EXISTS holder_credentials (
    id TEXT PRIMARY KEY,
    holder_id TEXT NOT NULL,
    credential_id TEXT NOT NULL,
    credential_digest BLOB NOT NULL,
    status TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    provisioned_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_holder_credential UNIQUE (holder_id, credential_id)
);

CREATE INDEX IF NOT EXISTS idx_holder_credentials_holder ON holder_credentials(holder_id, status);
CREATE INDEX IF NOT EXISTS idx_holder_credentials_credential ON holder_credentials(credential_id);
