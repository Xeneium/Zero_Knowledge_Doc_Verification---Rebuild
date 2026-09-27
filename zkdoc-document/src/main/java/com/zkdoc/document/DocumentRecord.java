package com.zkdoc.document;

import java.util.UUID;

public record DocumentRecord(
        UUID id, byte[] content, byte[] digest, byte[] signature, String publicToken, boolean active) {
}
