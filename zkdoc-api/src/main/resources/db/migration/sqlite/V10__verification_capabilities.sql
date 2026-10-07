-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V10__verification_capabilities.sql (SQLite)
-- Phase 11: QR & Public Verification Architecture
-- ==============================================================================

CREATE TABLE IF NOT EXISTS verification_capabilities (
    id TEXT PRIMARY KEY,
    capability_token TEXT UNIQUE NOT NULL,
    credential_id TEXT,
    document_id TEXT,
    mode TEXT NOT NULL,
    protocol_version TEXT NOT NULL DEFAULT 'QR_V1',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TEXT NOT NULL,
    revoked_at TEXT,
    revocation_reason TEXT,
    CONSTRAINT uq_capability_token UNIQUE (capability_token)
);

CREATE INDEX IF NOT EXISTS idx_capabilities_token ON verification_capabilities(capability_token);
CREATE INDEX IF NOT EXISTS idx_capabilities_status_exp ON verification_capabilities(status, expires_at);
CREATE INDEX IF NOT EXISTS idx_capabilities_credential ON verification_capabilities(credential_id);
CREATE INDEX IF NOT EXISTS idx_capabilities_document ON verification_capabilities(document_id);
