package com.zkdoc.api.audit;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Stores only verification metadata: actor, event type, target identifiers, time, and result. */
@Service
public class VerificationAuditService {
    private final JdbcTemplate jdbcTemplate;

    public VerificationAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(Authentication authentication, String eventType, UUID credentialId, UUID documentId, boolean result) {
        String actor = authentication != null && authentication.isAuthenticated()
                ? authentication.getName() : "anonymous";
        jdbcTemplate.update("""
                INSERT INTO verification_audit_events
                    (id, actor_subject, event_type, credential_id, document_id, result)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), actor, eventType,
                credentialId == null ? null : credentialId.toString(),
                documentId == null ? null : documentId.toString(), result ? 1 : 0);
    }
}
