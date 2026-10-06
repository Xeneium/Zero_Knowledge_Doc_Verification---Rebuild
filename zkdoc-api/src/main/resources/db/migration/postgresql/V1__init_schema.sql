-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V1__init_schema.sql (PostgreSQL 16+)
-- ==============================================================================

-- 1. Key Registry
CREATE TABLE key_registry (
    id UUID PRIMARY KEY,
    key_id VARCHAR(64) UNIQUE NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    algorithm VARCHAR(32) NOT NULL,
    public_key_pem TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'RETIRING', 'RETIRED', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revocation_reason TEXT,
    replacement_key_id VARCHAR(64)
);

CREATE INDEX idx_key_registry_purpose_status ON key_registry(purpose, status);

-- 2. Documents (Metadata & Capability Tokens)
CREATE TABLE documents (
    id UUID PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256_digest VARCHAR(64) NOT NULL,
    rsa_signature BYTEA NOT NULL,
    key_id VARCHAR(64) NOT NULL REFERENCES key_registry(key_id),
    public_token VARCHAR(64) UNIQUE NOT NULL,
    object_storage_key VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documents_digest ON documents(sha256_digest);
CREATE INDEX idx_documents_public_token ON documents(public_token);

-- 3. Verifiable Credentials
CREATE TABLE credentials (
    id UUID PRIMARY KEY,
    schema_id VARCHAR(255) NOT NULL,
    issuer_id VARCHAR(128) NOT NULL,
    key_id VARCHAR(64) NOT NULL REFERENCES key_registry(key_id),
    version INT NOT NULL DEFAULT 1,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED', 'EXPIRED')),
    canonical_digest BYTEA NOT NULL,
    rsa_signature BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attributes_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_credentials_issuer ON credentials(issuer_id, status);

-- 4. Attribute Commitments
CREATE TABLE attribute_commitments (
    id UUID PRIMARY KEY,
    credential_id UUID NOT NULL REFERENCES credentials(id) ON DELETE RESTRICT,
    attribute_name VARCHAR(128) NOT NULL,
    commitment_point BYTEA NOT NULL,
    blinding_factor_salt_hash VARCHAR(64) NOT NULL,
    attestation_signature BYTEA NOT NULL,
    attestation_key_id VARCHAR(64) NOT NULL REFERENCES key_registry(key_id),
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_credential_attribute UNIQUE (credential_id, attribute_name)
);

CREATE INDEX idx_commitments_credential ON attribute_commitments(credential_id);

-- 5. Verification Sessions (Anti-Replay Nonce Tracking)
CREATE TABLE verification_sessions (
    id UUID PRIMARY KEY,
    nonce VARCHAR(64) UNIQUE NOT NULL,
    credential_id UUID NOT NULL,
    attribute_name VARCHAR(128) NOT NULL,
    threshold BIGINT NOT NULL,
    verifier_identity VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_sessions_nonce ON verification_sessions(nonce);
CREATE INDEX idx_sessions_expires ON verification_sessions(expires_at);

-- 6. Verification Audit Events (Immutable Audit Trail)
CREATE TABLE verification_audit_events (
    id UUID PRIMARY KEY,
    correlation_id VARCHAR(64) NOT NULL,
    actor_subject VARCHAR(128) NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128),
    result VARCHAR(16) NOT NULL CHECK (result IN ('PASS', 'FAIL')),
    reason_code VARCHAR(64),
    client_ip_hash VARCHAR(64) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_correlation ON verification_audit_events(correlation_id);
CREATE INDEX idx_audit_timestamp ON verification_audit_events(timestamp);
