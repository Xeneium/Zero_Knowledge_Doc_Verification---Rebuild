package com.zkdoc.document;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDocumentStore implements DocumentStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(DocumentRecord document) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO document_records (id, content, digest, signature, public_token, active, owner_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, document.id().toString(), document.content(), document.digest(), document.signature(),
                    document.publicToken(), document.active(), document.ownerId());
        } catch (Exception ex) {
            // Fallback for database schemas where digest column is VARCHAR / TEXT instead of BYTEA / BLOB
            String hexDigest = HexFormat.of().formatHex(document.digest());
            jdbcTemplate.update("""
                    INSERT INTO document_records (id, content, digest, signature, public_token, active, owner_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, document.id().toString(), document.content(), hexDigest, document.signature(),
                    document.publicToken(), document.active(), document.ownerId());
        }
    }

    @Override
    public Optional<DocumentRecord> findById(UUID id) {
        return jdbcTemplate.query("""
                SELECT id, content, digest, signature, public_token, active, owner_id
                FROM document_records
                WHERE id = ?
                """, resultSet -> resultSet.next()
                ? Optional.of(mapRecord(resultSet))
                : Optional.empty(), id.toString());
    }

    @Override
    public Optional<DocumentRecord> findByPublicToken(String publicToken) {
        return jdbcTemplate.query("""
                SELECT id, content, digest, signature, public_token, active, owner_id
                FROM document_records
                WHERE public_token = ?
                """, resultSet -> resultSet.next()
                ? Optional.of(mapRecord(resultSet))
                : Optional.empty(), publicToken);
    }

    @Override
    public Optional<DocumentRecord> findByDigest(byte[] digest) {
        if (digest == null || digest.length == 0) {
            return Optional.empty();
        }
        // First try hex-encoded digest (PostgreSQL VARCHAR(64) / text schemas)
        String hex = HexFormat.of().formatHex(digest);
        List<DocumentRecord> hexResults = jdbcTemplate.query("""
                SELECT id, content, digest, signature, public_token, active, owner_id
                FROM document_records
                WHERE digest = ?
                """, (rs, rowNum) -> mapRecord(rs), hex);
        if (!hexResults.isEmpty()) {
            return Optional.of(hexResults.get(0));
        }

        // Also check raw binary parameter for binary BLOB schemas (e.g. SQLite legacy)
        try {
            List<DocumentRecord> results = jdbcTemplate.query("""
                    SELECT id, content, digest, signature, public_token, active, owner_id
                    FROM document_records
                    WHERE digest = ?
                    """, (rs, rowNum) -> mapRecord(rs), digest);
            if (!results.isEmpty()) {
                return Optional.of(results.get(0));
            }
        } catch (Exception ignored) {
            // Dialect uses VARCHAR/TEXT, binary comparison rejected
        }

        return Optional.empty();
    }

    @Override
    public List<DocumentRecord> findAllByOwnerId(String ownerId) {
        String safeOwnerId = (ownerId != null && !ownerId.isBlank()) ? ownerId.trim() : "system";
        return jdbcTemplate.query("""
                SELECT id, content, digest, signature, public_token, active, owner_id
                FROM document_records
                WHERE owner_id = ?
                ORDER BY created_at DESC
                """, (rs, rowNum) -> mapRecord(rs), safeOwnerId);
    }

    @Override
    public List<DocumentRecord> findAll() {
        return jdbcTemplate.query("""
                SELECT id, content, digest, signature, public_token, active, owner_id
                FROM document_records
                ORDER BY created_at DESC
                """, (rs, rowNum) -> mapRecord(rs));
    }

    private DocumentRecord mapRecord(ResultSet resultSet) throws SQLException {
        String ownerId = "system";
        try {
            ownerId = resultSet.getString("owner_id");
            if (ownerId == null) {
                ownerId = "system";
            }
        } catch (SQLException ignored) {
        }

        byte[] digestBytes;
        Object digestObj = resultSet.getObject("digest");
        if (digestObj instanceof byte[] b) {
            digestBytes = b;
        } else if (digestObj instanceof String s) {
            digestBytes = HexFormat.of().parseHex(s);
        } else {
            digestBytes = resultSet.getBytes("digest");
        }

        return new DocumentRecord(
                UUID.fromString(resultSet.getString("id")),
                resultSet.getBytes("content"),
                digestBytes,
                resultSet.getBytes("signature"),
                resultSet.getString("public_token"),
                resultSet.getBoolean("active"),
                ownerId);
    }
}
