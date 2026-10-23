-- ==============================================================================
-- Zero-Knowledge Document Verification Platform
-- Database Migration: V10__postgresql_schema_domain_store_compatibility.sql (PostgreSQL 16+)
-- Phase 2/14/15: Unify SQLite & PostgreSQL schema models for credentials & commitments
-- ==============================================================================

-- 1. Harmonize credentials table
ALTER TABLE credentials ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE credentials ALTER COLUMN schema_id DROP NOT NULL;
ALTER TABLE credentials ALTER COLUMN key_id DROP NOT NULL;
ALTER TABLE credentials ALTER COLUMN canonical_digest DROP NOT NULL;
ALTER TABLE credentials ALTER COLUMN rsa_signature DROP NOT NULL;
ALTER TABLE credentials ALTER COLUMN attributes_json DROP NOT NULL;

ALTER TABLE credentials ADD COLUMN IF NOT EXISTS canonical_payload BYTEA;
ALTER TABLE credentials ADD COLUMN IF NOT EXISTS digest BYTEA;
ALTER TABLE credentials ADD COLUMN IF NOT EXISTS signature BYTEA;

-- 2. Harmonize attribute_commitments table
ALTER TABLE attribute_commitments ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE attribute_commitments ALTER COLUMN commitment_point DROP NOT NULL;
ALTER TABLE attribute_commitments ALTER COLUMN blinding_factor_salt_hash DROP NOT NULL;
ALTER TABLE attribute_commitments ALTER COLUMN attestation_signature DROP NOT NULL;
ALTER TABLE attribute_commitments ALTER COLUMN attestation_key_id DROP NOT NULL;

ALTER TABLE attribute_commitments ADD COLUMN IF NOT EXISTS commitment BYTEA;
ALTER TABLE attribute_commitments ADD COLUMN IF NOT EXISTS attestation BYTEA;
ALTER TABLE attribute_commitments ADD COLUMN IF NOT EXISTS attestation_payload BYTEA;
ALTER TABLE attribute_commitments ADD COLUMN IF NOT EXISTS blinding_fingerprint BYTEA;

-- 3. Create bidirectional compatibility triggers for credentials
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
    IF NEW.key_id IS NULL THEN
        NEW.key_id := 'default-issuer-key-1';
    END IF;
    IF NEW.attributes_json IS NULL THEN
        NEW.attributes_json := '{}'::jsonb;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_credential_compat ON credentials;
CREATE TRIGGER trg_credential_compat BEFORE INSERT OR UPDATE ON credentials
FOR EACH ROW EXECUTE FUNCTION set_credential_compat_columns();

-- 4. Create bidirectional compatibility triggers for attribute_commitments
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
    IF NEW.attestation_key_id IS NULL THEN
        NEW.attestation_key_id := 'default-issuer-key-1';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_commitment_compat ON attribute_commitments;
CREATE TRIGGER trg_commitment_compat BEFORE INSERT OR UPDATE ON attribute_commitments
FOR EACH ROW EXECUTE FUNCTION set_commitment_compat_columns();
