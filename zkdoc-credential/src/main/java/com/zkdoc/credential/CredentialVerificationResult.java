package com.zkdoc.credential;

import java.util.UUID;

public record CredentialVerificationResult(UUID credentialId, boolean verified) {
}
