package com.zkdoc.credential;

import java.util.UUID;

public class CredentialNotFoundException extends RuntimeException {
    public CredentialNotFoundException(UUID credentialId) {
        super("Credential not found: " + credentialId);
    }
}
