package com.zkdoc.credential;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation of {@link CredentialLifecycleEventStore}.
 */
@Repository
public class JdbcCredentialLifecycleEventStore implements CredentialLifecycleEventStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCredentialLifecycleEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public void record(CredentialLifecycleEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        jdbcTemplate.update("""
                INSERT INTO credential_lifecycle_events (
                    id, credential_id, event_type, actor_id, actor_role, from_status, to_status, reason, timestamp, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.id().toString(),
                event.credentialId().toString(),
                event.eventType(),
                event.actor(),
                event.actorRole(),
                event.fromStatus().name(),
                event.toStatus().name(),
                event.reason(),
                event.timestamp().toString(),
                event.metadataJson());
    }

    @Override
    public List<CredentialLifecycleEvent> findByCredentialId(UUID credentialId) {
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        return jdbcTemplate.query("""
                SELECT id, credential_id, event_type, actor_id, actor_role, from_status, to_status, reason, timestamp, metadata_json
                FROM credential_lifecycle_events
                WHERE credential_id = ?
                ORDER BY timestamp ASC
                """,
                (rs, rowNum) -> map(rs),
                credentialId.toString());
    }

    private CredentialLifecycleEvent map(ResultSet rs) throws SQLException {
        return new CredentialLifecycleEvent(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("credential_id")),
                rs.getString("event_type"),
                rs.getString("actor_id"),
                rs.getString("actor_role"),
                CredentialStatus.valueOf(rs.getString("from_status")),
                CredentialStatus.valueOf(rs.getString("to_status")),
                rs.getString("reason"),
                Instant.parse(rs.getString("timestamp")),
                rs.getString("metadata_json"));
    }
}
