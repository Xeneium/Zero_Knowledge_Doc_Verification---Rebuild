package com.zkdoc.keys;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyLifecycleTest {

    @Test
    @DisplayName("ACTIVE status can sign and verify; transitions to RETIRING, RETIRED, or REVOKED")
    void activeKeyTransitions() {
        KeyStatus status = KeyStatus.ACTIVE;
        assertTrue(status.canSign());
        assertTrue(status.canVerifyHistorical());

        assertTrue(status.canTransitionTo(KeyStatus.RETIRING));
        assertTrue(status.canTransitionTo(KeyStatus.RETIRED));
        assertTrue(status.canTransitionTo(KeyStatus.REVOKED));
    }

    @Test
    @DisplayName("RETIRING status allows grace period verification and transition to RETIRED or REVOKED")
    void retiringKeyTransitions() {
        KeyStatus status = KeyStatus.RETIRING;
        assertFalse(status.canSign());
        assertTrue(status.canVerifyHistorical());

        assertTrue(status.canTransitionTo(KeyStatus.RETIRED));
        assertTrue(status.canTransitionTo(KeyStatus.REVOKED));
        assertFalse(status.canTransitionTo(KeyStatus.ACTIVE));
    }

    @Test
    @DisplayName("RETIRED status cannot sign, allows historical verification, and can transition only to REVOKED")
    void retiredKeyTransitions() {
        KeyStatus status = KeyStatus.RETIRED;
        assertFalse(status.canSign());
        assertTrue(status.canVerifyHistorical());

        assertTrue(status.canTransitionTo(KeyStatus.REVOKED));
        assertFalse(status.canTransitionTo(KeyStatus.ACTIVE));
        assertFalse(status.canTransitionTo(KeyStatus.RETIRING));
    }

    @Test
    @DisplayName("REVOKED status is terminal: cannot sign, cannot verify, cannot transition to any other status")
    void revokedKeyIsTerminal() {
        KeyStatus status = KeyStatus.REVOKED;
        assertFalse(status.canSign());
        assertFalse(status.canVerifyHistorical());

        assertFalse(status.canTransitionTo(KeyStatus.ACTIVE));
        assertFalse(status.canTransitionTo(KeyStatus.RETIRING));
        assertFalse(status.canTransitionTo(KeyStatus.RETIRED));
    }
}
