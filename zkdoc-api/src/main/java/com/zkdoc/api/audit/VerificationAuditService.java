package com.zkdoc.api.audit;

import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import static com.zkdoc.api.logging.MdcCorrelationIdFilter.MDC_KEY;

/**
 * Verification audit adapter preserving backward compatibility with existing controllers
 * while delegating to the production-hardened, tamper-evident {@link SecurityAuditService}.
 */
@Service
public class VerificationAuditService {

    private final JdbcTemplate jdbcTemplate;
    private final SecurityAuditService securityAuditService;

    public VerificationAuditService(
            JdbcTemplate jdbcTemplate,
            @Autowired(required = false) SecurityAuditService securityAuditService) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.securityAuditService = securityAuditService;
    }

    public void record(Authentication authentication, String eventType, UUID credentialId, UUID documentId, boolean result) {
        String actor = authentication != null && authentication.isAuthenticated()
                ? authentication.getName() : "anonymous";

        // 1. Maintain legacy audit record for backwards compatibility
        try {
            jdbcTemplate.update("""
                    INSERT INTO verification_audit_events
                        (id, actor_subject, event_type, credential_id, document_id, result)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID().toString(), actor, eventType,
                    credentialId == null ? null : credentialId.toString(),
                    documentId == null ? null : documentId.toString(), result ? 1 : 0);
        } catch (Exception ignored) {
            // Non-blocking for legacy table if schemas differ across environments
        }

        // 2. Delegate to tamper-evident canonical security audit service
        if (securityAuditService != null) {
            AuditEventType type = mapEventType(eventType, result);
            ActorType actorType = resolveActorType(authentication);
            TargetType targetType = credentialId != null ? TargetType.CREDENTIAL : (documentId != null ? TargetType.DOCUMENT : null);
            String targetId = credentialId != null ? credentialId.toString() : (documentId != null ? documentId.toString() : null);
            AuditOutcome outcome = result ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE;
            String correlationId = MDC.get(MDC_KEY);

            AuditEventRequest request = AuditEventRequest.builder(type)
                    .actor(actor, actorType)
                    .target(targetType, targetId)
                    .action(eventType)
                    .outcome(outcome)
                    .correlationId(correlationId)
                    .build();

            securityAuditService.record(request);
        }
    }

    private AuditEventType mapEventType(String eventType, boolean result) {
        if (eventType == null) {
            return result ? AuditEventType.CREDENTIAL_VERIFIED : AuditEventType.CREDENTIAL_VERIFY_FAILED;
        }
        return switch (eventType.toUpperCase()) {
            case "CREDENTIAL_ISSUED" -> AuditEventType.CREDENTIAL_ISSUED;
            case "DOCUMENT_UPLOADED" -> AuditEventType.DOCUMENT_UPLOADED;
            case "CREDENTIAL_VERIFY", "CREDENTIAL_VERIFY_HISTORICAL" ->
                    result ? AuditEventType.CREDENTIAL_VERIFIED : AuditEventType.CREDENTIAL_VERIFY_FAILED;
            case "CREDENTIAL_SUSPEND" -> AuditEventType.CREDENTIAL_SUSPENDED;
            case "CREDENTIAL_RESUME" -> AuditEventType.CREDENTIAL_RESUMED;
            case "CREDENTIAL_REVOKE" -> AuditEventType.CREDENTIAL_REVOKED;
            case "DOCUMENT_SERVER_COPY_VERIFY", "DOCUMENT_UPLOADED_COPY_VERIFY", "PUBLIC_DOCUMENT_VERIFY" ->
                    AuditEventType.DOCUMENT_INTEGRITY_VERIFIED;
            case "QR_CAPABILITY_CREATED" -> AuditEventType.QR_CAPABILITY_CREATED;
            case "QR_CAPABILITY_REVOKED" -> AuditEventType.QR_CAPABILITY_REVOKED;
            case "QR_VERIFICATION_ATTEMPT", "PUBLIC_VERIFICATION_ATTEMPT" -> AuditEventType.QR_VERIFICATION_ATTEMPT;
            case "QR_VERIFICATION_SUCCESS" -> AuditEventType.QR_VERIFICATION_SUCCESS;
            case "QR_VERIFICATION_FAILURE", "PUBLIC_VERIFICATION_FAILURE" -> AuditEventType.QR_VERIFICATION_FAILURE;
            case "CREDENTIAL_PREDICATE_VERIFY" ->
                    result ? AuditEventType.ZKP_VERIFICATION_SUCCESS : AuditEventType.ZKP_VERIFICATION_FAILURE;
            case "CREDENTIAL_PREDICATE_VERIFY_SATURATED" -> AuditEventType.RATE_LIMIT_TRIGGERED;
            case "HOLDER_CREDENTIAL_PROVISIONED" -> AuditEventType.HOLDER_CREDENTIAL_PROVISIONED;
            case "HOLDER_CREDENTIAL_ACCESSED" -> AuditEventType.HOLDER_CREDENTIAL_ACCESSED;
            case "HOLDER_CREDENTIAL_SUSPENDED" -> AuditEventType.CREDENTIAL_SUSPENDED;
            case "HOLDER_CREDENTIAL_ACTIVATED" -> AuditEventType.CREDENTIAL_RESUMED;
            case "HOLDER_CREDENTIAL_REVOKED" -> AuditEventType.CREDENTIAL_REVOKED;
            case "HOLDER_PREDICATE_PROOF_GENERATED" -> AuditEventType.ZKP_PROOF_GENERATION_SUCCESS;
            case "HOLDER_PREDICATE_PROOF_GENERATE_FAILED" -> AuditEventType.ZKP_PROOF_GENERATION_FAILURE;
            case "HOLDER_PREDICATE_PROOF_GENERATE_SATURATED" -> AuditEventType.RATE_LIMIT_TRIGGERED;
            default -> result ? AuditEventType.CREDENTIAL_VERIFIED : AuditEventType.CREDENTIAL_VERIFY_FAILED;
        };
    }

    private ActorType resolveActorType(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ActorType.ANONYMOUS_PUBLIC;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String role = ga.getAuthority();
            if ("ROLE_ADMIN".equals(role)) return ActorType.ADMIN;
            if ("ROLE_ISSUER".equals(role)) return ActorType.ISSUER;
            if ("ROLE_HOLDER".equals(role)) return ActorType.HOLDER;
            if ("ROLE_VERIFIER".equals(role)) return ActorType.VERIFIER;
        }
        return ActorType.USER;
    }
}
