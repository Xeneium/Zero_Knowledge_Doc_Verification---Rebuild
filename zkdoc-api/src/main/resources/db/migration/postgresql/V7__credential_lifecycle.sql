-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V7__credential_lifecycle.sql (PostgreSQL 16+)
-- Phase 14: Credential & Verification Lifecycle
-- ==============================================================================

-- 1. Ensure credential_id compatibility and lifecycle columns
ALTER TABLE credentials ADD COLUMN IF NOT EXISTS credential_id UUID;
UPDATE credentials SET credential_id = id WHERE credential_id IS NULL;

ALTER TABLE credentials ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ;
ALTER TABLE credentials ADD COLUMN IF NOT EXISTS suspension_reason TEXT;
ALTER TABLE credentials ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ;
ALTER TABLE credentials ADD COLUMN IF NOT EXISTS revocation_reason TEXT;

-- 2. Index for efficient status filtering
CREATE INDEX IF NOT EXISTS idx_credentials_status ON credentials(status);

-- 3. Immutable audit log for credential lifecycle transitions
CREATE TABLE IF NOT EXISTS credential_lifecycle_events (
    id UUID PRIMARY KEY,
    credential_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    reason TEXT,
    timestamp TIMESTAMPTZ NOT NULL,
    metadata_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_credential_lifecycle_events_cred ON credential_lifecycle_events(credential_id);
