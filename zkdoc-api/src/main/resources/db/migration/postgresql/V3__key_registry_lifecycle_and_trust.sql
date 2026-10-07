-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V3__key_registry_lifecycle_and_trust.sql (PostgreSQL 16+)
-- Phase 3: Cryptographic Key Lifecycle, Trust Registry & Audit Events
-- ==============================================================================

-- 1. Extend key_registry with full trust metadata, fingerprints, and versioning
ALTER TABLE key_registry
    ADD COLUMN IF NOT EXISTS owner_type VARCHAR(32) NOT NULL DEFAULT 'PLATFORM',
    ADD COLUMN IF NOT EXISTS algorithm_parameters VARCHAR(128) NOT NULL DEFAULT 'SHA-256/MGF1-SHA256/salt=32',
    ADD COLUMN IF NOT EXISTS public_key_der BYTEA,
    ADD COLUMN IF NOT EXISTS public_key_encoding VARCHAR(32) NOT NULL DEFAULT 'X.509/PEM',
    ADD COLUMN IF NOT EXISTS fingerprint VARCHAR(64),
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

-- 2. Indexes for trust resolution and audit lookup
CREATE INDEX IF NOT EXISTS idx_key_registry_fingerprint ON key_registry(fingerprint);
CREATE INDEX IF NOT EXISTS idx_key_registry_owner ON key_registry(owner_id, owner_type);
CREATE INDEX IF NOT EXISTS idx_key_registry_replacement ON key_registry(replacement_key_id);

-- 3. Key Lifecycle Audit Events table (immutable security audit trail)
CREATE TABLE IF NOT EXISTS key_lifecycle_audit_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    key_id VARCHAR(64) NOT NULL,
    purpose VARCHAR(32),
    owner_id VARCHAR(128),
    actor VARCHAR(128) NOT NULL DEFAULT 'system',
    result VARCHAR(32) NOT NULL,
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_key_audit_key_id ON key_lifecycle_audit_events(key_id, event_type);
CREATE INDEX IF NOT EXISTS idx_key_audit_created ON key_lifecycle_audit_events(created_at);
