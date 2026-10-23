package com.zkdoc.credential;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Shared JDBC persistence utilities for symmetric SQLite and PostgreSQL dialect compatibility.
 */
final class JdbcTypeUtils {
    private JdbcTypeUtils() {}

    static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    static Instant parseInstant(ResultSet rs, String column) {
        if (rs == null || column == null) {
            return null;
        }
        try {
            Object obj = rs.getObject(column);
            if (obj instanceof Timestamp ts) {
                return ts.toInstant();
            }
            if (obj instanceof OffsetDateTime odt) {
                return odt.toInstant();
            }
            if (obj instanceof Instant inst) {
                return inst;
            }
            if (obj instanceof Number num) {
                return Instant.ofEpochMilli(num.longValue());
            }
        } catch (Exception ignored) {
        }
        try {
            Timestamp ts = rs.getTimestamp(column);
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
                    return OffsetDateTime.parse(normalized).toInstant();
                } catch (Exception ignored) {
                }
                try {
                    return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC);
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
