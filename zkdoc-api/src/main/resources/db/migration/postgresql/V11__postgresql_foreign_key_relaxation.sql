-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V11__postgresql_foreign_key_relaxation.sql (PostgreSQL 16+)
-- Phase 2/14/15: Relax legacy constraints for artifact store & credential store
-- ==============================================================================

-- 1. Relax document_artifacts foreign key to allow document_records referencing
ALTER TABLE document_artifacts DROP CONSTRAINT IF EXISTS document_artifacts_document_id_fkey;

-- 2. Relax credentials foreign key on key_id
ALTER TABLE credentials DROP CONSTRAINT IF EXISTS credentials_key_id_fkey;

-- 3. Relax attribute_commitments foreign key on attestation_key_id
ALTER TABLE attribute_commitments DROP CONSTRAINT IF EXISTS attribute_commitments_attestation_key_id_fkey;

-- 4. Update trigger function set_credential_compat_columns to not insert dummy key_id
CREATE OR REPLACE FUNCTION set_credential_compat_columns() RETURNS trigger AS $$
BEGIN
    IF NEW.id IS NULL AND NEW.credential_id IS NOT NULL THEN
        NEW.id := NEW.credential_id;
    END IF;
    IF NEW.credential_id IS NULL AND NEW.id IS NOT NULL THEN
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

-- 5. Update trigger function set_commitment_compat_columns to not insert dummy attestation_key_id
CREATE OR REPLACE FUNCTION set_commitment_compat_columns() RETURNS trigger AS $$
BEGIN
    IF NEW.commitment_point IS NULL AND NEW.commitment IS NOT NULL THEN
        NEW.commitment_point := NEW.commitment;
    END IF;
    IF NEW.commitment IS NULL AND NEW.commitment_point IS NOT NULL THEN
        NEW.commitment := NEW.commitment_point;
    END IF;
    IF NEW.attestation_signature IS NULL AND NEW.attestation IS NOT NULL THEN
        NEW.attestation_signature := NEW.attestation;
    END IF;
    IF NEW.attestation IS NULL AND NEW.attestation_signature IS NOT NULL THEN
        NEW.attestation := NEW.attestation_signature;
    END IF;
    IF NEW.blinding_factor_salt_hash IS NULL THEN
        NEW.blinding_factor_salt_hash := 'sha256-synthetic-salt';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
