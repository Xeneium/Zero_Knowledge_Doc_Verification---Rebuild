package com.zkdoc.api.credential;

import jakarta.validation.constraints.Size;

public record ResumeCredentialRequest(
        @Size(max = 512, message = "reason must not exceed 512 characters")
        String reason) {
}
