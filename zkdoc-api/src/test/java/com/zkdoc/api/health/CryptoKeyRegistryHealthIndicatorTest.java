package com.zkdoc.api.health;

import com.zkdoc.keys.KeyRegistry;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoKeyRegistryHealthIndicatorTest {

    private static class StubKeyRegistry implements KeyRegistry {
        private final List<RegisteredKey> keys;
        private final RuntimeException failure;

        StubKeyRegistry(List<RegisteredKey> keys) {
            this.keys = keys;
            this.failure = null;
        }

        StubKeyRegistry(RuntimeException failure) {
            this.keys = null;
            this.failure = failure;
        }

        @Override
        public List<RegisteredKey> findAll() {
            if (failure != null) {
                throw failure;
            }
            return keys != null ? keys : List.of();
        }

        @Override
        public Optional<RegisteredKey> findByKeyId(String keyId) { return Optional.empty(); }

        @Override
        public Optional<RegisteredKey> findForVerification(String keyId, KeyPurpose purpose) { return Optional.empty(); }

        @Override
        public Optional<RegisteredKey> findForVerification(String keyId, KeyPurpose purpose, String expectedOwnerId) { return Optional.empty(); }

        @Override
        public Optional<RegisteredKey> findActiveForSigning(KeyPurpose purpose) { return Optional.empty(); }

        @Override
        public Optional<RegisteredKey> findActiveForSigning(KeyPurpose purpose, String ownerId) { return Optional.empty(); }

        @Override
        public List<RegisteredKey> findAllByOwner(String ownerId) { return List.of(); }

        @Override
        public void register(RegisteredKey key) {}

        @Override
        public void updateStatus(String keyId, KeyStatus status, String revocationReason, String replacementKeyId) {}

        @Override
        public void rotateKey(String oldKeyId, String newKeyId) {}
    }

    @Test
    @DisplayName("Health is UP when KeyRegistry lists registered keys successfully")
    void testHealthUp() {
        KeyRegistry keyRegistry = new StubKeyRegistry(List.of());

        CryptoKeyRegistryHealthIndicator indicator = new CryptoKeyRegistryHealthIndicator(keyRegistry);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "READY");
        assertThat(health.getDetails()).containsEntry("registeredKeys", 0);
    }

    @Test
    @DisplayName("Health is DOWN when KeyRegistry is null or unconfigured")
    void testHealthDownWhenNull() {
        CryptoKeyRegistryHealthIndicator indicator = new CryptoKeyRegistryHealthIndicator(null);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "UNAVAILABLE");
    }

    @Test
    @DisplayName("Health is DOWN without leaking exception details when KeyRegistry throws")
    void testHealthDownWhenException() {
        KeyRegistry keyRegistry = new StubKeyRegistry(new RuntimeException("Database timeout reading keys"));

        CryptoKeyRegistryHealthIndicator indicator = new CryptoKeyRegistryHealthIndicator(keyRegistry);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "DEGRADED");
        // Must never leak internal exception message or stack trace in health details
        assertThat(health.getDetails().get("reason").toString()).doesNotContain("Database timeout");
    }
}
