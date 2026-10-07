-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V2__canonical_domain_entities.sql (PostgreSQL 16+)
-- Phase 2 Canonical Domain Architecture & Persistence
-- ==============================================================================

-- 1. Issuers Table (Authoritative Issuer Domain Records)
CREATE TABLE issuers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issuer_id VARCHAR(128) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    signing_key_id VARCHAR(64) NOT NULL REFERENCES key_registry(key_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_issuers_status ON issuers(status);

-- 2. Credential Attributes Table (Explicitly Typed Normalized Attributes)
CREATE TABLE credential_attributes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credential_id UUID NOT NULL REFERENCES credentials(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL CHECK (type IN ('INTEGER', 'STRING', 'BOOLEAN', 'DATE', 'DATE_TIME', 'IDENTIFIER')),
    value TEXT NOT NULL,
    integer_value BIGINT,
    sort_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_credential_attribute_name UNIQUE (credential_id, name)
);

CREATE INDEX idx_credential_attributes_credential ON credential_attributes(credential_id, sort_order);

-- 3. Document Artifacts Table (Physical Representation Decoupled from Document Identity)
CREATE TABLE document_artifacts (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    artifact_version INT NOT NULL DEFAULT 1,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256_digest VARCHAR(64) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'DELETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_artifact_version UNIQUE (document_id, artifact_version)
);

CREATE INDEX idx_document_artifacts_doc ON document_artifacts(document_id, artifact_version);
