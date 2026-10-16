package com.zkdoc.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zkdoc.keys.DevelopmentSigningKeyProvider;
import com.zkdoc.keys.SigningKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("FIND-002, FIND-003, FIND-004 Remediation: Production Security Validator Tests")
class ProductionSecurityValidatorTest {

    private static final String STRONG_PASSWORD = "StrongSecureProdPassword#2026!";
    private static final String VALID_MASTER_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("Non-production environment skips validation without error")
    void nonProductionEnvironmentSkipsValidation() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                env, "insecure", true, false, "", null
        );

        assertThatCode(validator::validateConfiguration).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Production environment fails fast if demo seeding is enabled")
    void productionFailsFastIfDemoSeedingEnabled() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                env, STRONG_PASSWORD, true, true, VALID_MASTER_KEY, Mockito.mock(SigningKeyProvider.class)
        );

        assertThatThrownBy(validator::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zkdoc.demo.seed-enabled is strictly prohibited in production profiles");
    }

    @Test
    @DisplayName("Production environment fails fast if datasource password is empty")
    void productionFailsFastIfPasswordEmpty() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("production");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                env, "", false, true, VALID_MASTER_KEY, Mockito.mock(SigningKeyProvider.class)
        );

        assertThatThrownBy(validator::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database password (spring.datasource.password) is not configured");
    }

    @Test
    @DisplayName("Production environment fails fast if datasource password is a known default")
    void productionFailsFastIfPasswordIsDefault() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                env, "zkdoc_local_dev_secret", false, true, VALID_MASTER_KEY, Mockito.mock(SigningKeyProvider.class)
        );

        assertThatThrownBy(validator::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insecure or default database password detected");
    }

    @Test
    @DisplayName("Production environment fails fast if datasource password is shorter than 12 characters")
    void productionFailsFastIfPasswordTooShort() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                env, "Short#123", false, true, VALID_MASTER_KEY, Mockito.mock(SigningKeyProvider.class)
        );

        assertThatThrownBy(validator::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be at least 12 characters long");
    }

    @Test
    @DisplayName("Production environment fails fast if storage encryption is disabled")
    void productionFailsFastIfStorageEncryptionDisabled() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                env, STRONG_PASSWORD, false, false, "", Mockito.mock(SigningKeyProvider.class)
        );

        assertThatThrownBy(validator::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zkdoc.storage.encryption.enabled must be set to true");
    }

    @Test
    @DisplayName("Production environment fails fast if storage encryption master key is missing")
    void productionFailsFastIfMasterKeyMissing() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                env, STRONG_PASSWORD, false, true, "   ", Mockito.mock(SigningKeyProvider.class)
        );

        assertThatThrownBy(validator::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zkdoc.storage.encryption.master-key must be configured");
    }

    @Test
    @DisplayName("Production environment fails fast if DevelopmentSigningKeyProvider is active")
    void productionFailsFastIfDevelopmentKeyProviderActive() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                env, STRONG_PASSWORD, false, true, VALID_MASTER_KEY, new DevelopmentSigningKeyProvider()
        );

        assertThatThrownBy(validator::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DevelopmentSigningKeyProvider with ephemeral in-memory keys is strictly prohibited");
    }

    @Test
    @DisplayName("Production environment passes when all security gates are satisfied")
    void productionPassesWhenFullyHardened() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SigningKeyProvider nonDevProvider = Mockito.mock(SigningKeyProvider.class);

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                env, STRONG_PASSWORD, false, true, VALID_MASTER_KEY, nonDevProvider
        );

        assertThatCode(validator::validateConfiguration).doesNotThrowAnyException();
    }
}
