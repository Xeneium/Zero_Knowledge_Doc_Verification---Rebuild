package com.zkdoc.credential;

import java.util.UUID;

public class HolderCredentialNotFoundException extends RuntimeException {
    public HolderCredentialNotFoundException(UUID holderCredentialId) {
        super("Holder credential not found: " + holderCredentialId);
    }

    public HolderCredentialNotFoundException(String message) {
        super(message);
    }
}
