package com.zkdoc.api.audit;

/**
 * High-level security domain categories for audit taxonomy.
 */
public enum AuditEventCategory {
    AUTHENTICATION,
    AUTHORIZATION,
    CREDENTIAL,
    DOCUMENT,
    QR,
    ZKP,
    ABUSE,
    ADMIN,
    KEY
}
