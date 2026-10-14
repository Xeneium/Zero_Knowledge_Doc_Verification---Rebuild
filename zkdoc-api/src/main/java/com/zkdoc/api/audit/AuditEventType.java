package com.zkdoc.api.audit;

/**
 * Strongly typed taxonomy of all security-relevant audit event types.
 */
public enum AuditEventType {
    // Authentication
    AUTHENTICATION_SUCCESS(AuditEventCategory.AUTHENTICATION),
    AUTHENTICATION_FAILURE(AuditEventCategory.AUTHENTICATION),
    TOKEN_ISSUED(AuditEventCategory.AUTHENTICATION),
    TOKEN_REFRESHED(AuditEventCategory.AUTHENTICATION),
    TOKEN_REJECTED(AuditEventCategory.AUTHENTICATION),
    LOGOUT(AuditEventCategory.AUTHENTICATION),

    // Authorization
    ACCESS_GRANTED(AuditEventCategory.AUTHORIZATION),
    ACCESS_DENIED(AuditEventCategory.AUTHORIZATION),
    PRIVILEGE_VIOLATION(AuditEventCategory.AUTHORIZATION),
    OBJECT_ACCESS_DENIED(AuditEventCategory.AUTHORIZATION),

    // Credential Lifecycle
    CREDENTIAL_ISSUED(AuditEventCategory.CREDENTIAL),
    CREDENTIAL_REVOKED(AuditEventCategory.CREDENTIAL),
    CREDENTIAL_SUSPENDED(AuditEventCategory.CREDENTIAL),
    CREDENTIAL_RESUMED(AuditEventCategory.CREDENTIAL),
    CREDENTIAL_EXPIRED(AuditEventCategory.CREDENTIAL),
    CREDENTIAL_VERIFIED(AuditEventCategory.CREDENTIAL),
    CREDENTIAL_VERIFY_FAILED(AuditEventCategory.CREDENTIAL),
    HOLDER_CREDENTIAL_PROVISIONED(AuditEventCategory.CREDENTIAL),
    HOLDER_CREDENTIAL_ACCESSED(AuditEventCategory.CREDENTIAL),

    // Document
    DOCUMENT_UPLOADED(AuditEventCategory.DOCUMENT),
    DOCUMENT_INTEGRITY_VERIFIED(AuditEventCategory.DOCUMENT),
    DOCUMENT_ACCESS_GRANTED(AuditEventCategory.DOCUMENT),
    DOCUMENT_ACCESS_DENIED(AuditEventCategory.DOCUMENT),
    DOCUMENT_DELETED(AuditEventCategory.DOCUMENT),

    // QR Capability
    QR_CAPABILITY_CREATED(AuditEventCategory.QR),
    QR_CAPABILITY_REVOKED(AuditEventCategory.QR),
    QR_VERIFICATION_ATTEMPT(AuditEventCategory.QR),
    QR_VERIFICATION_SUCCESS(AuditEventCategory.QR),
    QR_VERIFICATION_FAILURE(AuditEventCategory.QR),

    // Zero-Knowledge Proofs
    ZKP_PROOF_GENERATION_ATTEMPT(AuditEventCategory.ZKP),
    ZKP_PROOF_GENERATION_SUCCESS(AuditEventCategory.ZKP),
    ZKP_PROOF_GENERATION_FAILURE(AuditEventCategory.ZKP),
    ZKP_VERIFICATION_ATTEMPT(AuditEventCategory.ZKP),
    ZKP_VERIFICATION_SUCCESS(AuditEventCategory.ZKP),
    ZKP_VERIFICATION_FAILURE(AuditEventCategory.ZKP),

    // Abuse & Rate Limiting
    RATE_LIMIT_TRIGGERED(AuditEventCategory.ABUSE),
    INVALID_SECURITY_REQUEST(AuditEventCategory.ABUSE),
    SUSPICIOUS_ACCESS(AuditEventCategory.ABUSE),
    SECURITY_POLICY_REJECTION(AuditEventCategory.ABUSE),

    // Administrative & Key Lifecycle
    ADMIN_SECURITY_OPERATION(AuditEventCategory.ADMIN),
    KEY_REGISTERED(AuditEventCategory.KEY),
    KEY_ROTATED(AuditEventCategory.KEY),
    KEY_REVOKED(AuditEventCategory.KEY);

    private final AuditEventCategory category;

    AuditEventType(AuditEventCategory category) {
        this.category = category;
    }

    public AuditEventCategory getCategory() {
        return category;
    }
}
