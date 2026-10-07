-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V4__holder_credentials.sql (PostgreSQL 16+)
-- Phase 6A: Holder Credential & Proving-Material Provisioning Architecture
-- ==============================================================================

CREATE TABLE IF NOT EXISTS holder_credentials (
    id UUID PRIMARY KEY,
    holder_id VARCHAR(128) NOT NULL,
    credential_id UUID NOT NULL REFERENCES credentials(id) ON DELETE RESTRICT,
    credential_digest BYTEA NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED', 'EXPIRED')),
    version INT NOT NULL DEFAULT 1,
    provisioned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_holder_credential UNIQUE (holder_id, credential_id)
);

CREATE INDEX IF NOT EXISTS idx_holder_credentials_holder ON holder_credentials(holder_id, status);
CREATE INDEX IF NOT EXISTS idx_holder_credentials_credential ON holder_credentials(credential_id);
