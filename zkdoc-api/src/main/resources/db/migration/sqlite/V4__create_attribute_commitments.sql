CREATE TABLE attribute_commitments (
    id TEXT PRIMARY KEY,
    credential_id TEXT NOT NULL,
    attribute_name TEXT NOT NULL,
    commitment BLOB NOT NULL,
    attestation BLOB NOT NULL,
    attestation_payload BLOB NOT NULL,
    blinding_fingerprint BLOB NOT NULL UNIQUE,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
