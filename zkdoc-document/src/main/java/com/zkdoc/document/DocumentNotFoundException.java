package com.zkdoc.document;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(UUID id) {
        super("Document not found: " + id);
    }

    public DocumentNotFoundException(String resource) {
        super("Document not found: " + resource);
    }
}
