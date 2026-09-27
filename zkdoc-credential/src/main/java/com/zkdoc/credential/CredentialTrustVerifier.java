package com.zkdoc.credential;

import java.util.UUID;

public interface CredentialTrustVerifier {
    boolean isActiveAndValid(UUID credentialId);
}
