package com.zkdoc.document;

import java.util.UUID;

public record VerificationResult(UUID documentId, boolean verified) {
}
