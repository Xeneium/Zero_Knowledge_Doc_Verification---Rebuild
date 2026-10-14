package com.zkdoc.api.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production-hardened JDBC implementation of {@link SecurityAuditService}.
 * Enforces atomic sequential ordering, tamper-evident SHA-256 hash chaining,
 * data minimization, and bounded administrative search capabilities.
 */
@Service
public class JdbcSecurityAuditService implements SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(JdbcSecurityAuditService.class);

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final Object chainLock = new Object();
    private LatestChainState cachedLatestState = null;

    public JdbcSecurityAuditService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    void invalidateCache() {
        synchronized (chainLock) {
            this.cachedLatestState = null;
        }
    }

    @Override
    @Transactional
    public SecurityAuditEvent record(AuditEventRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        // 1. Data minimization and secret exclusion
        String actor = AuditDataMinimizer.sanitizeString(request.actorSubject(), 128);
        String action = AuditDataMinimizer.sanitizeString(request.action(), 64);
        String targetId = AuditDataMinimizer.sanitizeString(request.targetId(), 128);
        String reasonCode = AuditDataMinimizer.sanitizeString(request.reasonCode(), 64);
        String correlationId = AuditDataMinimizer.sanitizeString(
                request.correlationId() != null && !request.correlationId().isBlank()
                        ? request.correlationId()
                        : UUID.randomUUID().toString(),
                64);
        String clientIpHash = AuditDataMinimizer.hashClientIp(request.clientIp());
        String metadataJson = AuditDataMinimizer.sanitizeMetadata(request.metadataJson());

        AuditDataMinimizer.assertNoSecretMaterial(actor, "actorSubject");
        AuditDataMinimizer.assertNoSecretMaterial(targetId, "targetId");
        AuditDataMinimizer.assertNoSecretMaterial(reasonCode, "reasonCode");
        AuditDataMinimizer.assertNoSecretMaterial(metadataJson, "metadataJson");

        UUID eventId = UUID.randomUUID();
        Instant timestamp = clock.instant();
        AuditEventType eventType = request.eventType();
        AuditEventCategory category = eventType.getCategory();

        synchronized (chainLock) {
            // 2. Fetch latest sequence and hash to maintain unbroken hash chain (cached under chainLock)
            if (cachedLatestState == null) {
                cachedLatestState = fetchLatestChainState();
            }
            long sequenceNumber = cachedLatestState.sequenceNumber() + 1;
            String previousHash = cachedLatestState.eventHash();

            // 3. Construct canonical serialization and compute integrity hash
            String canonical = AuditCanonicalizer.canonicalize(
                    sequenceNumber,
                    eventId,
                    eventType,
                    category,
                    timestamp,
                    actor,
                    request.actorType(),
                    request.targetType(),
                    targetId,
                    action,
                    request.outcome(),
                    reasonCode,
                    correlationId,
                    clientIpHash,
                    metadataJson);

            String eventHash = AuditCanonicalizer.computeHash(previousHash, canonical);

            // 4. Atomic append to durable audit store
            try {
                jdbcTemplate.update("""
                        INSERT INTO security_audit_events (
                            id, sequence_number, event_type, event_category, timestamp,
                            actor_subject, actor_type, target_type, target_id, action,
                            outcome, reason_code, correlation_id, client_ip_hash,
                            metadata_json, previous_event_hash, event_hash
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        eventId.toString(),
                        sequenceNumber,
                        eventType.name(),
                        category.name(),
                        timestamp.toString(),
                        actor,
                        request.actorType().name(),
                        request.targetType() != null ? request.targetType().name() : null,
                        targetId,
                        action,
                        request.outcome().name(),
                        reasonCode,
                        correlationId,
                        clientIpHash,
                        metadataJson,
                        previousHash,
                        eventHash);

                cachedLatestState = new LatestChainState(sequenceNumber, eventHash);
            } catch (RuntimeException e) {
                // Invalidate cached state so subsequent retries re-query durable state
                cachedLatestState = null;
                throw e;
            }

            return new SecurityAuditEvent(
                    eventId,
                    sequenceNumber,
                    eventType,
                    category,
                    timestamp,
                    actor,
                    request.actorType(),
                    request.targetType(),
                    targetId,
                    action,
                    request.outcome(),
                    reasonCode,
                    correlationId,
                    clientIpHash,
                    metadataJson,
                    previousHash,
                    eventHash);
        }
    }

    @Override
    public List<SecurityAuditEvent> findEvents(AuditQueryFilter filter) {
        StringBuilder sql = new StringBuilder("SELECT * FROM security_audit_events WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (filter.category() != null) {
            sql.append("AND event_category = ? ");
            params.add(filter.category().name());
        }
        if (filter.eventType() != null) {
            sql.append("AND event_type = ? ");
            params.add(filter.eventType().name());
        }
        if (filter.actorSubject() != null && !filter.actorSubject().isBlank()) {
            sql.append("AND actor_subject = ? ");
            params.add(filter.actorSubject().trim());
        }
        if (filter.targetType() != null) {
            sql.append("AND target_type = ? ");
            params.add(filter.targetType().name());
        }
        if (filter.targetId() != null && !filter.targetId().isBlank()) {
            sql.append("AND target_id = ? ");
            params.add(filter.targetId().trim());
        }
        if (filter.correlationId() != null && !filter.correlationId().isBlank()) {
            sql.append("AND correlation_id = ? ");
            params.add(filter.correlationId().trim());
        }
        if (filter.from() != null) {
            sql.append("AND timestamp >= ? ");
            params.add(filter.from().toString());
        }
        if (filter.to() != null) {
            sql.append("AND timestamp <= ? ");
            params.add(filter.to().toString());
        }

        sql.append("ORDER BY sequence_number DESC LIMIT ? OFFSET ?");
        params.add(filter.size());
        params.add(filter.page() * filter.size());

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapRow(rs), params.toArray());
    }

    @Override
    public long countEvents(AuditQueryFilter filter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM security_audit_events WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (filter.category() != null) {
            sql.append("AND event_category = ? ");
            params.add(filter.category().name());
        }
        if (filter.eventType() != null) {
            sql.append("AND event_type = ? ");
            params.add(filter.eventType().name());
        }
        if (filter.actorSubject() != null && !filter.actorSubject().isBlank()) {
            sql.append("AND actor_subject = ? ");
            params.add(filter.actorSubject().trim());
        }
        if (filter.targetType() != null) {
            sql.append("AND target_type = ? ");
            params.add(filter.targetType().name());
        }
        if (filter.targetId() != null && !filter.targetId().isBlank()) {
            sql.append("AND target_id = ? ");
            params.add(filter.targetId().trim());
        }
        if (filter.correlationId() != null && !filter.correlationId().isBlank()) {
            sql.append("AND correlation_id = ? ");
            params.add(filter.correlationId().trim());
        }
        if (filter.from() != null) {
            sql.append("AND timestamp >= ? ");
            params.add(filter.from().toString());
        }
        if (filter.to() != null) {
            sql.append("AND timestamp <= ? ");
            params.add(filter.to().toString());
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    @Override
    public AuditIntegrityResult verifyChainIntegrity() {
        return verifyChainIntegrity(1, Long.MAX_VALUE);
    }

    @Override
    public AuditIntegrityResult verifyChainIntegrity(long startSeq, long endSeq) {
        List<SecurityAuditEvent> events = jdbcTemplate.query("""
                SELECT * FROM security_audit_events
                WHERE sequence_number >= ? AND sequence_number <= ?
                ORDER BY sequence_number ASC
                """,
                (rs, rowNum) -> mapRow(rs),
                startSeq, endSeq);

        if (events.isEmpty()) {
            return AuditIntegrityResult.success(0);
        }

        long expectedSeq = events.get(0).sequenceNumber();
        String expectedPreviousHash;

        if (expectedSeq == 1) {
            expectedPreviousHash = AuditCanonicalizer.GENESIS_HASH;
        } else {
            // Retrieve actual previous event hash for sequence expectedSeq - 1
            expectedPreviousHash = jdbcTemplate.query("""
                    SELECT event_hash FROM security_audit_events WHERE sequence_number = ?
                    """,
                    rs -> rs.next() ? rs.getString("event_hash") : null,
                    expectedSeq - 1);

            if (expectedPreviousHash == null) {
                return AuditIntegrityResult.failure(0, expectedSeq, "Missing predecessor event for sequence " + expectedSeq);
            }
        }

        long checked = 0;
        for (SecurityAuditEvent event : events) {
            if (event.sequenceNumber() != expectedSeq) {
                return AuditIntegrityResult.failure(checked, event.sequenceNumber(),
                        "Sequence break detected. Expected " + expectedSeq + ", found " + event.sequenceNumber());
            }

            if (!AuditCanonicalizer.verify(event, expectedPreviousHash)) {
                return AuditIntegrityResult.failure(checked, event.sequenceNumber(),
                        "Integrity hash mismatch at sequence " + event.sequenceNumber() +
                        ". PreviousHash=" + event.previousEventHash() + ", RecordedHash=" + event.eventHash());
            }

            expectedPreviousHash = event.eventHash();
            expectedSeq++;
            checked++;
        }

        return AuditIntegrityResult.success(checked);
    }

    private LatestChainState fetchLatestChainState() {
        return jdbcTemplate.query("""
                SELECT sequence_number, event_hash
                FROM security_audit_events
                ORDER BY sequence_number DESC
                LIMIT 1
                """,
                rs -> {
                    if (rs.next()) {
                        return new LatestChainState(
                                rs.getLong("sequence_number"),
                                rs.getString("event_hash")
                        );
                    }
                    return new LatestChainState(0L, AuditCanonicalizer.GENESIS_HASH);
                });
    }

    private SecurityAuditEvent mapRow(ResultSet rs) throws SQLException {
        String targetTypeStr = rs.getString("target_type");
        TargetType targetType = targetTypeStr != null && !targetTypeStr.isBlank()
                ? TargetType.valueOf(targetTypeStr)
                : null;

        return new SecurityAuditEvent(
                UUID.fromString(rs.getString("id")),
                rs.getLong("sequence_number"),
                AuditEventType.valueOf(rs.getString("event_type")),
                AuditEventCategory.valueOf(rs.getString("event_category")),
                Instant.parse(rs.getString("timestamp")),
                rs.getString("actor_subject"),
                ActorType.valueOf(rs.getString("actor_type")),
                targetType,
                rs.getString("target_id"),
                rs.getString("action"),
                AuditOutcome.valueOf(rs.getString("outcome")),
                rs.getString("reason_code"),
                rs.getString("correlation_id"),
                rs.getString("client_ip_hash"),
                rs.getString("metadata_json"),
                rs.getString("previous_event_hash"),
                rs.getString("event_hash")
        );
    }

    private record LatestChainState(long sequenceNumber, String eventHash) {}
}
