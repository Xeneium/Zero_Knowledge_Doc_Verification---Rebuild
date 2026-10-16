package com.zkdoc.api.security;

import com.zkdoc.keys.DevelopmentSigningKeyProvider;
import com.zkdoc.keys.SigningKeyProvider;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Startup production security validator enforcing fail-fast controls (FIND-001, FIND-002, FIND-003, FIND-004).
 * <p>
 * Ensures that if the application boots under production profiles ('prod', 'production'):
 * 1. Demo seeding is strictly disabled.
 * 2. Database credentials are non-empty, strong, and do not match insecure defaults.
 * 3. Document storage envelope encryption is active and possesses valid key material.
 * 4. Ephemeral development signing key providers are strictly forbidden.
 */
@Component
public class ProductionSecurityValidator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecurityValidator.class);

    private static final Set<String> BANNED_PASSWORDS = Set.of(
            "zkdoc_local_dev_secret",
            "dev_password_change_in_prod",
            "postgres",
            "admin",
            "password",
            "root",
            "123456",
            "changeme",
            "test",
            "demo"
    );

    private final Environment environment;
    private final String datasourcePassword;
    private final boolean demoSeedEnabled;
    private final boolean storageEncryptionEnabled;
    private final String storageEncryptionMasterKey;
    private final SigningKeyProvider signingKeyProvider;

    @Autowired
    public ProductionSecurityValidator(
            Environment environment,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${zkdoc.demo.seed-enabled:false}") boolean demoSeedEnabled,
            @Value("${zkdoc.storage.encryption.enabled:true}") boolean storageEncryptionEnabled,
            @Value("${zkdoc.storage.encryption.master-key:}") String storageEncryptionMasterKey,
            @Autowired(required = false) SigningKeyProvider signingKeyProvider) {
        this.environment = environment;
        this.datasourcePassword = datasourcePassword;
        this.demoSeedEnabled = demoSeedEnabled;
        this.storageEncryptionEnabled = storageEncryptionEnabled;
        this.storageEncryptionMasterKey = storageEncryptionMasterKey;
        this.signingKeyProvider = signingKeyProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validateConfiguration();
    }

    public void validateConfiguration() {
        if (environment == null || !environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            log.info("Non-production environment active; production security gating skipped.");
            return;
        }

        log.info("Production profile detected ('prod'/'production'); executing rigorous fail-fast security pre-flight checks...");

        // 1. Validate Demo Seed Disabled (FIND-001)
        if (demoSeedEnabled) {
            throw new IllegalStateException(
                    "CRITICAL PRODUCTION SECURITY ERROR: zkdoc.demo.seed-enabled is strictly prohibited in production profiles ('prod', 'production')!");
        }

        // 2. Validate Database Password Strength & Absence of Defaults (FIND-002)
        if (datasourcePassword == null || datasourcePassword.trim().isEmpty()) {
            throw new IllegalStateException(
                    "CRITICAL PRODUCTION SECURITY ERROR: Database password (spring.datasource.password) is not configured for production environment!");
        }

        String trimmedPassword = datasourcePassword.trim();
        if (BANNED_PASSWORDS.contains(trimmedPassword.toLowerCase())) {
            throw new IllegalStateException(
                    "CRITICAL PRODUCTION SECURITY ERROR: Insecure or default database password detected in production environment! (" + trimmedPassword + ")");
        }

        if (trimmedPassword.length() < 12) {
            throw new IllegalStateException(
                    "CRITICAL PRODUCTION SECURITY ERROR: Production database password must be at least 12 characters long (length: "
                            + trimmedPassword.length() + ")");
        }

        // 3. Validate Storage Envelope Encryption (FIND-003)
        if (!storageEncryptionEnabled) {
            throw new IllegalStateException(
                    "CRITICAL PRODUCTION SECURITY ERROR: zkdoc.storage.encryption.enabled must be set to true in production profile!");
        }

        if (storageEncryptionMasterKey == null || storageEncryptionMasterKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "CRITICAL PRODUCTION SECURITY ERROR: zkdoc.storage.encryption.master-key must be configured with a 256-bit key in production profile!");
        }

        // 4. Validate Production Signing Key Custody (FIND-004)
        if (signingKeyProvider instanceof DevelopmentSigningKeyProvider) {
            throw new IllegalStateException(
                    "CRITICAL PRODUCTION SECURITY ERROR: DevelopmentSigningKeyProvider with ephemeral in-memory keys is strictly prohibited in production profile!");
        }

        log.info("Production security pre-flight checks passed successfully.");
    }
}
