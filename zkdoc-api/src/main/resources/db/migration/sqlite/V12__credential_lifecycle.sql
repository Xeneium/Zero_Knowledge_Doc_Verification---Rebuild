-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V12__credential_lifecycle.sql (SQLite)
-- Phase 14: Credential & Verification Lifecycle
-- ==============================================================================

-- 1. Add lifecycle state tracking and optimistic locking columns to credentials
ALTER TABLE credentials ADD COLUMN suspended_at TEXT;
ALTER TABLE credentials ADD COLUMN suspension_reason TEXT;
ALTER TABLE credentials ADD COLUMN revoked_at TEXT;
ALTER TABLE credentials ADD COLUMN revocation_reason TEXT;
ALTER TABLE credentials ADD COLUMN version INTEGER NOT NULL DEFAULT 1;

-- 2. Index for efficient status filtering
CREATE INDEX IF NOT EXISTS idx_credentials_status ON credentials(status);

-- 3. Immutable audit log for credential lifecycle transitions
CREATE TABLE IF NOT EXISTS credential_lifecycle_events (
    id TEXT PRIMARY KEY,
    credential_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    actor_role TEXT NOT NULL,
    from_status TEXT NOT NULL,
    to_status TEXT NOT NULL,
    reason TEXT,
    timestamp TEXT NOT NULL,
    metadata_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_credential_lifecycle_events_cred ON credential_lifecycle_events(credential_id);
