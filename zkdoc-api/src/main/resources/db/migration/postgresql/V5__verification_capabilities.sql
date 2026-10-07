-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V5__verification_capabilities.sql (PostgreSQL 16+)
-- Phase 11: QR & Public Verification Architecture
-- ==============================================================================

CREATE TABLE IF NOT EXISTS verification_capabilities (
    id UUID PRIMARY KEY,
    capability_token VARCHAR(64) UNIQUE NOT NULL,
    credential_id UUID REFERENCES credentials(id) ON DELETE RESTRICT,
    document_id UUID REFERENCES documents(id) ON DELETE RESTRICT,
    mode VARCHAR(32) NOT NULL CHECK (mode IN ('MODE_A_PRESENTED_ARTIFACT', 'MODE_B_REGISTRY_CAPABILITY')),
    protocol_version VARCHAR(16) NOT NULL DEFAULT 'QR_V1',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED', 'DISABLED')),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_capabilities_token ON verification_capabilities(capability_token);
CREATE INDEX IF NOT EXISTS idx_capabilities_status_exp ON verification_capabilities(status, expires_at);
CREATE INDEX IF NOT EXISTS idx_capabilities_credential ON verification_capabilities(credential_id);
CREATE INDEX IF NOT EXISTS idx_capabilities_document ON verification_capabilities(document_id);
