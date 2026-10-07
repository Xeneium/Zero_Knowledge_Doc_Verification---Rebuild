-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V7__create_canonical_domain_entities.sql (SQLite Local)
-- Phase 2 Canonical Domain Architecture & Persistence
-- ==============================================================================

-- 1. Issuers Table
CREATE TABLE issuers (
    id TEXT PRIMARY KEY,
    issuer_id TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    signing_key_id TEXT NOT NULL REFERENCES key_registry(key_id),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Credential Attributes Table
CREATE TABLE credential_attributes (
    id TEXT PRIMARY KEY,
    credential_id TEXT NOT NULL REFERENCES credentials(credential_id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    value TEXT NOT NULL,
    integer_value INTEGER,
    sort_order INTEGER NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_credential_attribute_name UNIQUE (credential_id, name)
);

-- 3. Document Artifacts Table
CREATE TABLE document_artifacts (
    id TEXT PRIMARY KEY,
    document_id TEXT NOT NULL,
    artifact_version INTEGER NOT NULL DEFAULT 1,
    content_type TEXT NOT NULL,
    file_size INTEGER NOT NULL,
    sha256_digest TEXT NOT NULL,
    storage_key TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_artifact_version UNIQUE (document_id, artifact_version)
);
