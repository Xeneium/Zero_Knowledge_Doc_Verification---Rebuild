-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V9__key_registry_fingerprint_length.sql (PostgreSQL 16+)
-- Fix: Expand key_registry.fingerprint to allow full 'SHA256:<64-hex>' (71 chars)
-- ==============================================================================

ALTER TABLE key_registry ALTER COLUMN fingerprint TYPE VARCHAR(128);
