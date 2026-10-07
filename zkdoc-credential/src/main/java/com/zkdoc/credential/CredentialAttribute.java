package com.zkdoc.credential;

import java.util.Objects;

/**
 * Immutable credential attribute associating a normalized name with a typed value.
 */
public record CredentialAttribute(
        AttributeName attributeName,
        AttributeType type,
        AttributeValue attributeValue) {

    public CredentialAttribute {
        Objects.requireNonNull(attributeName, "attributeName must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(attributeValue, "attributeValue must not be null");
        if (attributeValue.type() != type) {
            throw new IllegalArgumentException(
                    "Attribute value type " + attributeValue.type() + " does not match declared type " + type);
        }
    }

    /**
     * Backward-compatible constructor defaulting to STRING type.
     */
    public CredentialAttribute(String name, String value) {
        this(AttributeName.of(name), AttributeType.STRING, AttributeValue.ofString(value));
    }

    /**
     * Convenience constructor parsing a raw string per specified type.
     */
    public CredentialAttribute(String name, AttributeType type, String value) {
        this(AttributeName.of(name), type, AttributeValue.ofRaw(type, value));
    }

    /**
     * Convenience constructor for integer attributes.
     */
    public CredentialAttribute(String name, long integerValue) {
        this(AttributeName.of(name), AttributeType.INTEGER, AttributeValue.ofInteger(integerValue));
    }

    /**
     * Convenience constructor for boolean attributes.
     */
    public CredentialAttribute(String name, boolean booleanValue) {
        this(AttributeName.of(name), AttributeType.BOOLEAN, AttributeValue.ofBoolean(booleanValue));
    }

    /**
     * Backward-compatible name getter.
     */
    public String name() {
        return attributeName.value();
    }

    /**
     * Backward-compatible raw string value getter.
     */
    public String value() {
        return attributeValue.rawString();
    }

    @Override
    public String toString() {
        return "CredentialAttribute[name=" + name() + ", type=" + type + "]";
    }
}
