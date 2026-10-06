CREATE TABLE key_registry (
    id TEXT PRIMARY KEY,
    key_id TEXT NOT NULL UNIQUE,
    purpose TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED', 'REVOKED')),
    algorithm TEXT NOT NULL,
    public_key_pem TEXT NOT NULL,
    private_key_file_environment TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retired_at TEXT,
    revoked_at TEXT
);

CREATE INDEX key_registry_purpose_status_idx ON key_registry(purpose, status);
