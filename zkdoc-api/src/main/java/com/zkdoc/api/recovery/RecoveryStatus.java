package com.zkdoc.api.recovery;

/**
 * Status indicator for restored database, artifacts, trust state, and audit log.
 */
public enum RecoveryStatus {
    HEALTHY,
    WARNING,
    CORRUPTED
}
