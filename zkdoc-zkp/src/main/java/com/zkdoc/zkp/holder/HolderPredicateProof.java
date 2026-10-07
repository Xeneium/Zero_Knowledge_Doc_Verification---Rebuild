package com.zkdoc.zkp.holder;

import com.zkdoc.zkp.engine.PredicateProofArtifact;
import java.util.Objects;

/**
 * Public, holder-facing zero-knowledge predicate proof package.
 * <p>
 * Combines the cryptographic {@link PredicateProofArtifact} (Bulletproof R1CS range proof)
 * with the authoritative {@link PublicPredicateStatement}.
 * <p>
 * SECURITY GUARANTEE:
 * Contains zero sensitive witness material (no raw attribute, no blinding scalar).
 * Can be safely transmitted to verifiers or serialized into presentation responses.
 */
public record HolderPredicateProof(
        PublicPredicateStatement statement,
        PredicateProofArtifact proofArtifact
) {
    public HolderPredicateProof {
        Objects.requireNonNull(statement, "statement must not be null");
        Objects.requireNonNull(proofArtifact, "proofArtifact must not be null");
    }
}
