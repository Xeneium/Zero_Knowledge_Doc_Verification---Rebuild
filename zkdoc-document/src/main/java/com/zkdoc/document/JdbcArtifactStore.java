package com.zkdoc.document;

import com.zkdoc.core.crypto.Digest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation of ArtifactStore.
 */
@Repository
public class JdbcArtifactStore implements ArtifactStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcArtifactStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(DocumentArtifact artifact) {
        jdbcTemplate.update("""
                INSERT INTO document_artifacts (
                    id, document_id, artifact_version, content_type, file_size, sha256_digest, storage_key, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                artifact.id().value(),
                artifact.documentId().value(),
                artifact.artifactVersion(),
                artifact.contentType(),
                artifact.fileSize(),
                artifact.digest().toHex(),
                artifact.storageKey(),
                artifact.status().name(),
                toTimestamp(artifact.createdAt()));
    }

    @Override
    public Optional<DocumentArtifact> findById(ArtifactId id) {
        return jdbcTemplate.query("""
                SELECT id, document_id, artifact_version, content_type, file_size, sha256_digest, storage_key, status, created_at
                FROM document_artifacts
                WHERE id = ?
                """,
                resultSet -> resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty(),
                id.value());
    }

    @Override
    public List<DocumentArtifact> findByDocumentId(DocumentId documentId) {
        return jdbcTemplate.query("""
                SELECT id, document_id, artifact_version, content_type, file_size, sha256_digest, storage_key, status, created_at
                FROM document_artifacts
                WHERE document_id = ?
                ORDER BY artifact_version DESC
                """,
                (rs, rowNum) -> map(rs),
                documentId.value());
    }

    private DocumentArtifact map(ResultSet rs) throws SQLException {
        return new DocumentArtifact(
                ArtifactId.fromString(rs.getString("id")),
                DocumentId.fromString(rs.getString("document_id")),
                rs.getInt("artifact_version"),
                rs.getString("content_type"),
                rs.getLong("file_size"),
                Digest.fromHex(rs.getString("sha256_digest")),
                rs.getString("storage_key"),
                ArtifactStatus.valueOf(rs.getString("status")),
                parseInstant(rs, "created_at"));
    }

    private static java.sql.Timestamp toTimestamp(Instant instant) {
        return instant != null ? java.sql.Timestamp.from(instant) : null;
    }

    private static Instant parseInstant(ResultSet rs, String column) {
        try {
            Object obj = rs.getObject(column);
            if (obj instanceof java.sql.Timestamp ts) {
                return ts.toInstant();
            }
            if (obj instanceof java.time.OffsetDateTime odt) {
                return odt.toInstant();
            }
            if (obj instanceof java.time.Instant inst) {
                return inst;
            }
            if (obj instanceof Number num) {
                return Instant.ofEpochMilli(num.longValue());
            }
        } catch (Exception ignored) {
        }
        try {
            java.sql.Timestamp ts = rs.getTimestamp(column);
            if (ts != null) {
                return ts.toInstant();
            }
        } catch (Exception ignored) {
        }
        try {
            String str = rs.getString(column);
            if (str != null && !str.isBlank()) {
                String trimmed = str.trim();
                if (trimmed.chars().allMatch(Character::isDigit)) {
                    try {
                        return Instant.ofEpochMilli(Long.parseLong(trimmed));
                    } catch (Exception ignored) {
                    }
                }
                String normalized = trimmed.replace(" ", "T");
                try {
                    return Instant.parse(normalized);
                } catch (Exception ignored) {
                }
                try {
                    return java.time.OffsetDateTime.parse(normalized).toInstant();
                } catch (Exception ignored) {
                }
                try {
                    return java.time.LocalDateTime.parse(normalized).toInstant(java.time.ZoneOffset.UTC);
                } catch (Exception ignored) {
                }
                if (!normalized.endsWith("Z") && !normalized.contains("+")
                        && !normalized.substring(Math.min(10, normalized.length())).contains("-")) {
                    try {
                        return Instant.parse(normalized + "Z");
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
