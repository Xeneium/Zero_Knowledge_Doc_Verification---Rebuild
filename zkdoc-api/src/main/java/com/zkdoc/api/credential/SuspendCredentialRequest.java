package com.zkdoc.api.credential;

import jakarta.validation.constraints.Size;

public record SuspendCredentialRequest(
        @Size(max = 512, message = "reason must not exceed 512 characters")
        String reason) {
}
