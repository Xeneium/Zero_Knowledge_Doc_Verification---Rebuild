package com.zkdoc.api.health;

import com.zkdoc.keys.KeyRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * HealthIndicator verifying that the cryptographic key registry is loaded and healthy.
 * <p>
 * Strictly minimizes disclosure: exposes key counts and operational state,
 * but never leaks private key material, key bytes, or sensitive identifiers to callers.
 */
@Component
public class CryptoKeyRegistryHealthIndicator implements HealthIndicator {

    private final KeyRegistry keyRegistry;

    public CryptoKeyRegistryHealthIndicator(@Autowired(required = false) KeyRegistry keyRegistry) {
        this.keyRegistry = keyRegistry;
    }

    @Override
    public Health health() {
        if (keyRegistry == null) {
            return Health.down()
                    .withDetail("status", "UNAVAILABLE")
                    .withDetail("reason", "Key registry service is not configured")
                    .build();
        }

        try {
            int keyCount = keyRegistry.findAll().size();
            return Health.up()
                    .withDetail("status", "READY")
                    .withDetail("registeredKeys", keyCount)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("status", "DEGRADED")
                    .withDetail("reason", "Failed to query cryptographic key registry")
                    .build();
        }
    }
}
