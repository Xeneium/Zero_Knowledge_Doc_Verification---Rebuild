-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V12__postgresql_credential_id_sync_and_capabilities_fk.sql (PostgreSQL 16+)
-- Phase 2/11/14: Fix credential ID alignment & decouple foreign keys for hybrid stores
-- ==============================================================================

-- 1. Relax foreign key constraints across hybrid stores
ALTER TABLE verification_capabilities DROP CONSTRAINT IF EXISTS verification_capabilities_document_id_fkey;
ALTER TABLE verification_capabilities DROP CONSTRAINT IF EXISTS verification_capabilities_credential_id_fkey;
ALTER TABLE attribute_commitments DROP CONSTRAINT IF EXISTS attribute_commitments_credential_id_fkey;
ALTER TABLE holder_credentials DROP CONSTRAINT IF EXISTS holder_credentials_credential_id_fkey;

-- 2. Update trigger to prioritize credential_id over default gen_random_uuid()
CREATE OR REPLACE FUNCTION set_credential_compat_columns() RETURNS trigger AS $$
BEGIN
    IF NEW.credential_id IS NOT NULL THEN
        NEW.id := NEW.credential_id;
    ELSIF NEW.id IS NOT NULL THEN
        NEW.credential_id := NEW.id;
    END IF;
    IF NEW.canonical_digest IS NULL AND NEW.digest IS NOT NULL THEN
        NEW.canonical_digest := NEW.digest;
    END IF;
    IF NEW.digest IS NULL AND NEW.canonical_digest IS NOT NULL THEN
        NEW.digest := NEW.canonical_digest;
    END IF;
    IF NEW.rsa_signature IS NULL AND NEW.signature IS NOT NULL THEN
        NEW.rsa_signature := NEW.signature;
    END IF;
    IF NEW.signature IS NULL AND NEW.rsa_signature IS NOT NULL THEN
        NEW.signature := NEW.rsa_signature;
    END IF;
    IF NEW.schema_id IS NULL THEN
        NEW.schema_id := 'urn:zkdoc:schema:default';
    END IF;
    IF NEW.attributes_json IS NULL THEN
        NEW.attributes_json := '{}'::jsonb;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 3. Clean up any unaligned demo credentials so DemoDataInitializer re-seeds cleanly
DELETE FROM credentials WHERE id != credential_id;
