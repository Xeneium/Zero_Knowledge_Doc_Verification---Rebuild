-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V8__key_registry_lifecycle_and_trust.sql (SQLite)
-- Phase 3: Cryptographic Key Lifecycle, Trust Registry & Audit Events
-- ==============================================================================

-- 1. Extend key_registry with metadata, fingerprints, owner, and lifecycle columns
ALTER TABLE key_registry ADD COLUMN owner_id TEXT NOT NULL DEFAULT 'platform';
ALTER TABLE key_registry ADD COLUMN owner_type TEXT NOT NULL DEFAULT 'PLATFORM';
ALTER TABLE key_registry ADD COLUMN algorithm_parameters TEXT NOT NULL DEFAULT 'SHA-256/MGF1-SHA256/salt=32';
ALTER TABLE key_registry ADD COLUMN public_key_der BLOB;
ALTER TABLE key_registry ADD COLUMN public_key_encoding TEXT NOT NULL DEFAULT 'X.509/PEM';
ALTER TABLE key_registry ADD COLUMN fingerprint TEXT;
ALTER TABLE key_registry ADD COLUMN activated_at TEXT;
ALTER TABLE key_registry ADD COLUMN revocation_reason TEXT;
ALTER TABLE key_registry ADD COLUMN replacement_key_id TEXT;
ALTER TABLE key_registry ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE key_registry ADD COLUMN metadata TEXT NOT NULL DEFAULT '{}';

-- 2. Indexes for trust resolution and audit lookup
CREATE INDEX IF NOT EXISTS idx_key_registry_fingerprint ON key_registry(fingerprint);
CREATE INDEX IF NOT EXISTS idx_key_registry_owner ON key_registry(owner_id, owner_type);
CREATE INDEX IF NOT EXISTS idx_key_registry_replacement ON key_registry(replacement_key_id);

-- 3. Key Lifecycle Audit Events table (immutable security audit trail)
CREATE TABLE IF NOT EXISTS key_lifecycle_audit_events (
    id TEXT PRIMARY KEY,
    event_type TEXT NOT NULL,
    key_id TEXT NOT NULL,
    purpose TEXT,
    owner_id TEXT,
    actor TEXT NOT NULL DEFAULT 'system',
    result TEXT NOT NULL,
    detail TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_key_audit_key_id ON key_lifecycle_audit_events(key_id, event_type);
CREATE INDEX IF NOT EXISTS idx_key_audit_created ON key_lifecycle_audit_events(created_at);
