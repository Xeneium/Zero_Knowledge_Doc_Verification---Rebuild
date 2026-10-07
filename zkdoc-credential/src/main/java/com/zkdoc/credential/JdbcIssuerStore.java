package com.zkdoc.credential;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC persistence adapter for the Issuer entity.
 * Keeps persistence mapping cleanly decoupled from the domain entity.
 */
@Repository
public class JdbcIssuerStore implements IssuerStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcIssuerStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Issuer issuer) {
        jdbcTemplate.update("""
                INSERT INTO issuers (issuer_id, name, status, signing_key_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                issuer.id().value(),
                issuer.name(),
                issuer.status().name(),
                issuer.signingKeyId().value(),
                issuer.createdAt().toString(),
                issuer.updatedAt().toString());
    }

    @Override
    public Optional<Issuer> findById(IssuerId id) {
        return jdbcTemplate.query("""
                SELECT issuer_id, name, status, signing_key_id, created_at, updated_at
                FROM issuers
                WHERE issuer_id = ?
                """,
                resultSet -> resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty(),
                id.value());
    }

    @Override
    public void updateStatus(IssuerId id, IssuerStatus status) {
        jdbcTemplate.update("""
                UPDATE issuers
                SET status = ?, updated_at = ?
                WHERE issuer_id = ?
                """,
                status.name(),
                Instant.now().toString(),
                id.value());
    }

    private Issuer map(ResultSet rs) throws SQLException {
        return new Issuer(
                IssuerId.of(rs.getString("issuer_id")),
                rs.getString("name"),
                IssuerStatus.valueOf(rs.getString("status")),
                KeyId.of(rs.getString("signing_key_id")),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at")));
    }
}
