package com.zkdoc.credential;

public record SignedCredential(Credential credential, byte[] canonicalPayload, byte[] digest, byte[] signature) {
}
