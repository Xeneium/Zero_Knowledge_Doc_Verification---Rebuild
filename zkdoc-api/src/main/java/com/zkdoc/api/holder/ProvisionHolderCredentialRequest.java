package com.zkdoc.api.holder;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ProvisionHolderCredentialRequest(
        @NotNull(message = "credentialId must not be null")
        UUID credentialId) {
}
