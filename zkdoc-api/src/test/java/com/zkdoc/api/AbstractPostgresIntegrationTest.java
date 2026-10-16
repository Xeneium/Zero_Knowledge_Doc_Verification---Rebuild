package com.zkdoc.api;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Reusable abstract base class for integration tests against real PostgreSQL 16+
 * managed by Testcontainers.
 * Automatically runs when Docker daemon is available (CI / local containerized dev).
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("postgres")
@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker daemon is not running on this host; test will execute in CI.")
public abstract class AbstractPostgresIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> postgres = isDockerAvailable()
            ? new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("zkdoc_test")
                    .withUsername("zkdoc_test")
                    .withPassword("zkdoc_test")
            : null;

    public static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isPostgresAvailable() {
        String extUrl = System.getenv("ZKDOC_TEST_POSTGRES_URL");
        if (extUrl != null && !extUrl.isBlank()) {
            return true;
        }
        return isDockerAvailable();
    }

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        String extUrl = System.getenv("ZKDOC_TEST_POSTGRES_URL");
        if (extUrl != null && !extUrl.isBlank()) {
            String user = System.getenv().getOrDefault("ZKDOC_TEST_POSTGRES_USER", "zkdoc_app");
            String pass = System.getenv().getOrDefault("ZKDOC_TEST_POSTGRES_PASSWORD", "");
            registry.add("spring.datasource.url", () -> extUrl);
            registry.add("spring.datasource.username", () -> user);
            registry.add("spring.datasource.password", () -> pass);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.locations", () -> "classpath:db/migration/postgresql");
        } else if (isDockerAvailable() && postgres != null && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.locations", () -> "classpath:db/migration/postgresql");
        }
    }
}
