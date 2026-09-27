package com.zkdoc.credential;

import java.util.Objects;

public record CredentialAttribute(String name, String value) {
    public CredentialAttribute {
        Objects.requireNonNull(name, "attribute name must not be null");
        Objects.requireNonNull(value, "attribute value must not be null");
    }
}
