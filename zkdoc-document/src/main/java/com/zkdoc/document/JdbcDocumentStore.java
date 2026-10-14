package com.zkdoc.document;

import java.sql.ResultSet;
import java.sql.SQLException;
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
        jdbcTemplate.update("""
                INSERT INTO document_records (id, content, digest, signature, public_token, active, owner_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, document.id().toString(), document.content(), document.digest(), document.signature(),
                document.publicToken(), document.active(), document.ownerId());
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

    private DocumentRecord mapRecord(ResultSet resultSet) throws SQLException {
        String ownerId = "system";
        try {
            ownerId = resultSet.getString("owner_id");
            if (ownerId == null) {
                ownerId = "system";
            }
        } catch (SQLException ignored) {
        }
        return new DocumentRecord(
                UUID.fromString(resultSet.getString("id")),
                resultSet.getBytes("content"),
                resultSet.getBytes("digest"),
                resultSet.getBytes("signature"),
                resultSet.getString("public_token"),
                resultSet.getBoolean("active"),
                ownerId);
    }
}
