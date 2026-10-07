package com.zkdoc.zkp;

import com.zkdoc.credential.HolderCredentialId;
import com.zkdoc.credential.HolderId;

/**
 * Service SPI governing secure access to holder proving material.
 * <p>
 * Forms the strict zero-custody and BOLA/IDOR protection boundary through which provers
 * retrieve proving contexts. Verifiers and unauthenticated actors CANNOT access this interface.
 */
public interface HolderProvingMaterialAccess {

    /**
     * Resolves and cryptographically verifies the complete proving context for a given holder attribute.
     *
     * @param holderId The authenticated holder's subject ID
     * @param holderCredentialId The unique holder credential ID
     * @param attributeName The targeted credential attribute name
     * @return Fully validated HolderProofContext ready for proof generation
     * @throws com.zkdoc.credential.HolderCredentialNotFoundException if the holder does not own the credential
     * @throws IllegalStateException if the credential is not ACTIVE or cryptographic continuity checks fail
     */
    HolderProofContext resolveProofContext(HolderId holderId, HolderCredentialId holderCredentialId, String attributeName);
}
