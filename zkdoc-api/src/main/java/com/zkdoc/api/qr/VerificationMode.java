package com.zkdoc.api.qr;

public enum VerificationMode {
    /**
     * Mode A: Verifier presents document or byte content to verify against server-anchored digest/signature.
     */
    MODE_A_PRESENTED_ARTIFACT,

    /**
     * Mode B: Verification capability resolves directly to trusted server-side record/credential state.
     */
    MODE_B_REGISTRY_CAPABILITY
}
