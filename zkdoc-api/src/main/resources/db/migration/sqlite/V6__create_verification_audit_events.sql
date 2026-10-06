CREATE TABLE verification_audit_events (
    id TEXT PRIMARY KEY,
    actor_subject TEXT NOT NULL,
    event_type TEXT NOT NULL,
    credential_id TEXT,
    document_id TEXT,
    result INTEGER NOT NULL,
    occurred_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
