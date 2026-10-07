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
                artifact.id().value().toString(),
                artifact.documentId().value().toString(),
                artifact.artifactVersion(),
                artifact.contentType(),
                artifact.fileSize(),
                artifact.digest().toHex(),
                artifact.storageKey(),
                artifact.status().name(),
                artifact.createdAt().toString());
    }

    @Override
    public Optional<DocumentArtifact> findById(ArtifactId id) {
        return jdbcTemplate.query("""
                SELECT id, document_id, artifact_version, content_type, file_size, sha256_digest, storage_key, status, created_at
                FROM document_artifacts
                WHERE id = ?
                """,
                resultSet -> resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty(),
                id.value().toString());
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
                documentId.value().toString());
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
                Instant.parse(rs.getString("created_at")));
    }
}
