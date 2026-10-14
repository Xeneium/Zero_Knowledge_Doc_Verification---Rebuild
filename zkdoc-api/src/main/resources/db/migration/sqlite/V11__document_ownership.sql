-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V11__document_ownership.sql (SQLite)
-- Phase 12: Authentication, Authorization & Object-Level Security
-- ==============================================================================

-- 1. Add owner_id to document_records for object-level authorization (BOLA/IDOR defense)
ALTER TABLE document_records ADD COLUMN owner_id TEXT DEFAULT 'system';

-- 2. Index for efficient owner-scoped document queries
CREATE INDEX IF NOT EXISTS idx_document_records_owner ON document_records(owner_id);
