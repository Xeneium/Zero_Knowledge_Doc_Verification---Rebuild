package com.zkdoc.zkp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Holder-side proving material store SPI.
 * Represents the storage abstraction for private witness and opening material
 * (such as blinding factor r and attribute value v) required for future Bulletproof range proofs.
 * <p>
 * In production, this can be implemented by an encrypted client-side wallet, HSM, or
 * envelope-encrypted vault. It is NEVER accessible through verifier endpoints or general public APIs.
 */
public interface HolderProvingMaterialStore {

    /**
     * Stores secret opening material for a specific holder and credential.
     */
    void save(String holderId, AttributeOpeningSecret secret);

    /**
     * Retrieves secret opening material strictly for the authorized holder subject.
     */
    Optional<AttributeOpeningSecret> findByHolderAndCredentialAndAttribute(
            String holderId, UUID credentialId, String attributeName);

    /**
     * Lists all opening secrets held by this holder for a specific credential.
     */
    List<AttributeOpeningSecret> findAllByHolderAndCredential(String holderId, UUID credentialId);

    /**
     * Purges secret opening material when a holder credential is permanently revoked or destroyed.
     */
    void deleteByHolderAndCredential(String holderId, UUID credentialId);
}
