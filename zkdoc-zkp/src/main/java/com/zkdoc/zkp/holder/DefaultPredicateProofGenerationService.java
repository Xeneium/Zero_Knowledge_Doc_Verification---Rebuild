package com.zkdoc.zkp.holder;

import com.zkdoc.credential.HolderId;
import com.zkdoc.zkp.HolderProofContext;
import com.zkdoc.zkp.HolderProvingMaterialAccess;
import com.zkdoc.zkp.engine.AttributePredicate;
import com.zkdoc.zkp.engine.BulletproofPredicateEngine;
import com.zkdoc.zkp.engine.PredicateOperator;
import com.zkdoc.zkp.engine.PredicateProofArtifact;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Production implementation of {@link PredicateProofGenerationService}.
 * <p>
 * Orchestrates the full defense-in-depth zero-knowledge predicate proof workflow:
 * <ol>
 *   <li>Ownership & Object-Level Authorization (Phase 6B IDOR protection via {@link HolderProvingMaterialAccess}).</li>
 *   <li>Holder Credential & Authoritative Credential Lifecycle verification (fails closed if not ACTIVE).</li>
 *   <li>Credential Digest & Blinding Fingerprint integrity checks.</li>
 *   <li>Cryptographic continuity check: \( C = vG + rH \) verified against authentic registered commitment point.</li>
 *   <li>Predicate domain satisfaction check: verifies \( v \ge \text{threshold} \).</li>
 *   <li>Proof generation via {@link BulletproofPredicateEngine} without leaking secrets.</li>
 *   <li>Explicit zeroization of ephemeral witness memory.</li>
 *   <li>Assembles immutable {@link HolderPredicateProof} containing solely public statement data.</li>
 * </ol>
 */
@Service
public class DefaultPredicateProofGenerationService implements PredicateProofGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPredicateProofGenerationService.class);

    private final HolderProvingMaterialAccess provingMaterialAccess;
    private final BulletproofPredicateEngine predicateEngine;

    public DefaultPredicateProofGenerationService(
            HolderProvingMaterialAccess provingMaterialAccess,
            BulletproofPredicateEngine predicateEngine) {
        this.provingMaterialAccess = Objects.requireNonNull(provingMaterialAccess, "provingMaterialAccess must not be null");
        this.predicateEngine = Objects.requireNonNull(predicateEngine, "predicateEngine must not be null");
    }

    @Override
    public HolderPredicateProof generateProof(HolderId holderId, HolderPredicateProofRequest request) {
        Objects.requireNonNull(holderId, "holderId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (request.operator() != PredicateOperator.GREATER_THAN_OR_EQUAL) {
            throw new IllegalArgumentException("Unsupported predicate operator: " + request.operator());
        }

        // 1. Authorize holder & resolve secure proving context (IDOR, lifecycle, digest, opening checks)
        HolderProofContext context = provingMaterialAccess.resolveProofContext(
                holderId, request.holderCredentialId(), request.attributeName());

        PrivateProofWitness witness = null;
        try {
            // 2. Validate predicate satisfaction in plaintext before proving
            AttributePredicate predicate = request.toPredicate();
            long plaintextValue = context.attributeValue();
            if (!predicate.satisfies(plaintextValue)) {
                log.warn("Predicate generation rejected: Attribute value does not satisfy requested threshold for holder={}",
                        holderId);
                throw new IllegalArgumentException(
                        "Predicate constraint not satisfied: attribute does not satisfy threshold requirement");
            }

            // 3. Construct ephemeral private witness
            witness = new PrivateProofWitness(plaintextValue, context.blindingFactor());

            // 4. Invoke cryptographic engine to generate real Bulletproof range proof
            PredicateProofArtifact proofArtifact = predicateEngine.proveGreaterThanOrEqual(
                    witness.attributeValue(),
                    witness.blindingFactor(),
                    request.threshold(),
                    request.transcriptNonce()
            );

            // 5. Construct public predicate statement
            PublicPredicateStatement statement = new PublicPredicateStatement(
                    context.holderCredentialId(),
                    context.credentialId(),
                    context.credentialDigest(),
                    predicate,
                    context.commitmentPoint(),
                    context.attestationSignature(),
                    context.attestationKeyId(),
                    request.transcriptNonce()
            );

            log.info("Successfully generated zero-knowledge predicate proof for holder={}, credential={}, attribute={}",
                    holderId, context.credentialId(), request.attributeName());

            return new HolderPredicateProof(statement, proofArtifact);

        } finally {
            // Defense-in-depth: destroy witness and context memory
            if (witness != null) {
                witness.destroy();
            }
            context.destroy();
        }
    }
}
