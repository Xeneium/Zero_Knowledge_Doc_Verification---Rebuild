ALTER TABLE document_records
    ADD COLUMN public_token TEXT;

ALTER TABLE document_records
    ADD COLUMN active INTEGER NOT NULL DEFAULT 1;

-- Backfill existing records before making the opaque-token column mandatory.
UPDATE document_records
SET public_token = lower(hex(randomblob(32)))
WHERE public_token IS NULL;

CREATE UNIQUE INDEX document_records_public_token_key
    ON document_records(public_token);
