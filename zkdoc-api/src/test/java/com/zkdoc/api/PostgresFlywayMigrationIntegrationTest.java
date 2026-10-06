package com.zkdoc.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Validates that the PostgreSQL 16+ production schema and Flyway migrations
 * execute successfully and establish all required foundation tables.
 */
@SpringBootTest(classes = Application.class)
@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker daemon is not running on this host; test will execute in CI.")
class PostgresFlywayMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("PostgreSQL Flyway migration establishes all foundation tables")
    void flywayCreatesAllTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class
        );

        assertTrue(tables.contains("key_registry"), "key_registry table must exist");
        assertTrue(tables.contains("documents"), "documents table must exist");
        assertTrue(tables.contains("credentials"), "credentials table must exist");
        assertTrue(tables.contains("attribute_commitments"), "attribute_commitments table must exist");
        assertTrue(tables.contains("verification_sessions"), "verification_sessions table must exist");
        assertTrue(tables.contains("verification_audit_events"), "verification_audit_events table must exist");
        assertTrue(tables.contains("flyway_schema_history"), "flyway_schema_history table must exist");
    }
}
