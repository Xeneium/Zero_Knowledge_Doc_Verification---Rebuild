package com.zkdoc.document;

/** Deliberately minimal representation safe for the unauthenticated verifier. */
public record PublicVerificationResult(boolean signatureValid, boolean documentActive) {
}
