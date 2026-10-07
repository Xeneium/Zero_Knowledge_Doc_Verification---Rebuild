package com.zkdoc.api.publicverify;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CapabilityPublicVerificationResponse(
        boolean verified,
        String status,
        String mode,
        String protocolVersion,
        boolean signatureValid,
        boolean active,
        String failureReason
) {
    public static CapabilityPublicVerificationResponse success(String mode, String protocolVersion) {
        return new CapabilityPublicVerificationResponse(
                true,
                "VALID",
                mode,
                protocolVersion,
                true,
                true,
                null
        );
    }

    public static CapabilityPublicVerificationResponse failure(String mode, String protocolVersion, String reason) {
        return new CapabilityPublicVerificationResponse(
                false,
                "INVALID",
                mode,
                protocolVersion,
                false,
                false,
                reason
        );
    }
}
