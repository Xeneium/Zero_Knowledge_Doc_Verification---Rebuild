package com.zkdoc.credential;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Immutable, strongly-typed representation of a credential attribute value.
 * Standardizes Unicode NFC normalization, deterministic date/time formats,
 * and signed 64-bit integer values.
 */
public final class AttributeValue {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AttributeType type;
    private final String rawNormalizedString;
    private final Long integerVal;
    private final Boolean booleanVal;
    private final LocalDate dateVal;
    private final Instant dateTimeVal;

    private AttributeValue(
            AttributeType type,
            String rawNormalizedString,
            Long integerVal,
            Boolean booleanVal,
            LocalDate dateVal,
            Instant dateTimeVal) {
        this.type = type;
        this.rawNormalizedString = rawNormalizedString;
        this.integerVal = integerVal;
        this.booleanVal = booleanVal;
        this.dateVal = dateVal;
        this.dateTimeVal = dateTimeVal;
    }

    public static AttributeValue ofInteger(long value) {
        return new AttributeValue(AttributeType.INTEGER, Long.toString(value), value, null, null, null);
    }

    public static AttributeValue ofString(String value) {
        Objects.requireNonNull(value, "String value must not be null");
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        return new AttributeValue(AttributeType.STRING, normalized, null, null, null, null);
    }

    public static AttributeValue ofBoolean(boolean value) {
        return new AttributeValue(AttributeType.BOOLEAN, Boolean.toString(value), null, value, null, null);
    }

    public static AttributeValue ofDate(LocalDate date) {
        Objects.requireNonNull(date, "LocalDate must not be null");
        return new AttributeValue(AttributeType.DATE, date.format(DATE_FORMATTER), null, null, date, null);
    }

    public static AttributeValue ofDateTime(Instant instant) {
        Objects.requireNonNull(instant, "Instant must not be null");
        return new AttributeValue(AttributeType.DATE_TIME, instant.toString(), null, null, null, instant);
    }

    public static AttributeValue ofIdentifier(String id) {
        Objects.requireNonNull(id, "Identifier value must not be null");
        String normalized = Normalizer.normalize(id.trim(), Normalizer.Form.NFC);
        return new AttributeValue(AttributeType.IDENTIFIER, normalized, null, null, null, null);
    }

    public static AttributeValue ofRaw(AttributeType type, String rawValue) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(rawValue, "rawValue must not be null");
        return switch (type) {
            case INTEGER -> ofInteger(Long.parseLong(rawValue.trim()));
            case STRING -> ofString(rawValue);
            case BOOLEAN -> ofBoolean(Boolean.parseBoolean(rawValue.trim()));
            case DATE -> ofDate(LocalDate.parse(rawValue.trim(), DATE_FORMATTER));
            case DATE_TIME -> ofDateTime(Instant.parse(rawValue.trim()));
            case IDENTIFIER -> ofIdentifier(rawValue);
        };
    }

    public AttributeType type() {
        return type;
    }

    public String rawString() {
        return rawNormalizedString;
    }

    public long asInteger() {
        if (type != AttributeType.INTEGER) {
            throw new IllegalStateException("Attribute value is not an INTEGER, but: " + type);
        }
        return integerVal;
    }

    public boolean asBoolean() {
        if (type != AttributeType.BOOLEAN) {
            throw new IllegalStateException("Attribute value is not a BOOLEAN, but: " + type);
        }
        return booleanVal;
    }

    public LocalDate asDate() {
        if (type != AttributeType.DATE) {
            throw new IllegalStateException("Attribute value is not a DATE, but: " + type);
        }
        return dateVal;
    }

    public Instant asDateTime() {
        if (type != AttributeType.DATE_TIME) {
            throw new IllegalStateException("Attribute value is not a DATE_TIME, but: " + type);
        }
        return dateTimeVal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AttributeValue that = (AttributeValue) o;
        return type == that.type && Objects.equals(rawNormalizedString, that.rawNormalizedString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, rawNormalizedString);
    }

    @Override
    public String toString() {
        return rawNormalizedString;
    }
}
