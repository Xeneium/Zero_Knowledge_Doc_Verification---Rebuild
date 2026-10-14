package com.zkdoc.api.audit;

/**
 * Classification of security targets affected by an audit event.
 */
public enum TargetType {
    CREDENTIAL,
    DOCUMENT,
    QR_CAPABILITY,
    USER,
    KEY,
    SYSTEM
}
