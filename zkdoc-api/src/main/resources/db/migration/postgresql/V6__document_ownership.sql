-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V6__document_ownership.sql (PostgreSQL)
-- Phase 12: Authentication, Authorization & Object-Level Security
-- ==============================================================================

-- 1. Create document_records table if not exists for JdbcDocumentStore compatibility
CREATE TABLE IF NOT EXISTS document_records (
    id VARCHAR(64) PRIMARY KEY,
    content BYTEA NOT NULL,
    digest VARCHAR(64) NOT NULL,
    signature BYTEA NOT NULL,
    public_token VARCHAR(64),
    active BOOLEAN DEFAULT TRUE,
    owner_id VARCHAR(128) DEFAULT 'system',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE document_records ADD COLUMN IF NOT EXISTS owner_id VARCHAR(128) DEFAULT 'system';
CREATE INDEX IF NOT EXISTS idx_document_records_owner ON document_records(owner_id);

-- 2. Add owner_id to documents table for canonical document records
ALTER TABLE documents ADD COLUMN IF NOT EXISTS owner_id VARCHAR(128) DEFAULT 'system';
CREATE INDEX IF NOT EXISTS idx_documents_owner ON documents(owner_id);
