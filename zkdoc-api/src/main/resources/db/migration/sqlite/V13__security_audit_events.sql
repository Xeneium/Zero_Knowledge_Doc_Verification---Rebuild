-- V13: Security Audit Events (Immutable, Tamper-Evident Security Audit Trail)
CREATE TABLE security_audit_events (
    id TEXT PRIMARY KEY,
    sequence_number INTEGER NOT NULL UNIQUE,
    event_type TEXT NOT NULL,
    event_category TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    actor_subject TEXT NOT NULL,
    actor_type TEXT NOT NULL,
    target_type TEXT,
    target_id TEXT,
    action TEXT NOT NULL,
    outcome TEXT NOT NULL,
    reason_code TEXT,
    correlation_id TEXT NOT NULL,
    client_ip_hash TEXT NOT NULL,
    metadata_json TEXT,
    previous_event_hash TEXT NOT NULL,
    event_hash TEXT NOT NULL
);

CREATE INDEX idx_sec_audit_timestamp ON security_audit_events(timestamp);
CREATE INDEX idx_sec_audit_category ON security_audit_events(event_category);
CREATE INDEX idx_sec_audit_type ON security_audit_events(event_type);
CREATE INDEX idx_sec_audit_actor ON security_audit_events(actor_subject);
CREATE INDEX idx_sec_audit_target ON security_audit_events(target_type, target_id);
CREATE INDEX idx_sec_audit_correlation ON security_audit_events(correlation_id);
CREATE INDEX idx_sec_audit_seq ON security_audit_events(sequence_number);
