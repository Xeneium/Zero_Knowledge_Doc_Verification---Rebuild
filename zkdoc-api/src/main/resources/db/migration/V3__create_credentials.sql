CREATE TABLE credentials (
    credential_id TEXT PRIMARY KEY,
    issuer_id TEXT NOT NULL,
    issued_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    status TEXT NOT NULL,
    canonical_payload BLOB NOT NULL,
    digest BLOB NOT NULL,
    signature BLOB NOT NULL
);
