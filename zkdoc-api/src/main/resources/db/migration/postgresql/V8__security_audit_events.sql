-- V8: Security Audit Events (Immutable, Tamper-Evident Security Audit Trail)
CREATE TABLE security_audit_events (
    id UUID PRIMARY KEY,
    sequence_number BIGSERIAL NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    event_category VARCHAR(32) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    actor_subject VARCHAR(128) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    target_type VARCHAR(64),
    target_id VARCHAR(128),
    action VARCHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    reason_code VARCHAR(64),
    correlation_id VARCHAR(64) NOT NULL,
    client_ip_hash VARCHAR(64) NOT NULL,
    metadata_json VARCHAR(1024),
    previous_event_hash VARCHAR(64) NOT NULL,
    event_hash VARCHAR(64) NOT NULL
);

CREATE INDEX idx_sec_audit_timestamp ON security_audit_events(timestamp);
CREATE INDEX idx_sec_audit_category ON security_audit_events(event_category);
CREATE INDEX idx_sec_audit_type ON security_audit_events(event_type);
CREATE INDEX idx_sec_audit_actor ON security_audit_events(actor_subject);
CREATE INDEX idx_sec_audit_target ON security_audit_events(target_type, target_id);
CREATE INDEX idx_sec_audit_correlation ON security_audit_events(correlation_id);
CREATE INDEX idx_sec_audit_seq ON security_audit_events(sequence_number);
