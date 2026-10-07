package com.zkdoc.zkp.holder;

import com.zkdoc.credential.HolderId;

/**
 * Service SPI governing the generation of zero-knowledge predicate proofs by authorized holders.
 * <p>
 * Forms the core defense-in-depth boundary connecting Phase 6B proving-material security
 * to Phase 7A Bulletproof range-proof cryptographic engines.
 */
public interface PredicateProofGenerationService {

    /**
     * Authenticates, authorizes, and evaluates a zero-knowledge predicate proof on behalf of an authorized holder.
     *
     * @param holderId Authenticated holder subject principal
     * @param request Typed request containing holder credential ID, attribute name, operator, threshold, and nonce
     * @return Immutable {@link HolderPredicateProof} containing public statement and cryptographic proof artifact
     * @throws com.zkdoc.credential.HolderCredentialNotFoundException if holder does not own the credential
     * @throws IllegalStateException if credential is not active, digest mismatch, or commitment opening fails
     * @throws IllegalArgumentException if predicate is unsatisfied (\( v < \text{threshold} \)) or parameters invalid
     */
    HolderPredicateProof generateProof(HolderId holderId, HolderPredicateProofRequest request);
}
