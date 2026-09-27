package com.zkdoc.zkp;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.zkdoc.credential.CanonicalCredentialEncoder;
import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.credential.CredentialSha256Hasher;
import com.weavechain.ec.Scalar;
import com.weavechain.zk.bulletproofs.BulletProofGenerators;
import com.weavechain.zk.bulletproofs.PedersenCommitment;
import com.weavechain.zk.bulletproofs.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PedersenCommitmentServiceTest {
    @Test
    void sameValueWithDifferentBlindingsProducesDifferentCommitmentBytes() {
        InMemoryCommitmentStore store = new InMemoryCommitmentStore();
        PedersenCommitmentService service = new PedersenCommitmentService(
                store, new CanonicalCredentialEncoder(), new CredentialSha256Hasher(), new CredentialRsaPssSigner(),
                new SequenceBlindingFactorSource(bytes(1), bytes(2)));
        UUID credentialId = UUID.fromString("714b523f-4285-41e1-8d7f-42e1d0caa001");

        AttributeCommitment first = service.commit(credentialId, "age", 21L);
        AttributeCommitment second = service.commit(credentialId, "age", 21L);

        assertFalse(java.util.Arrays.equals(first.commitment(), second.commitment()));
    }

    @Test
    void rejectsARepeatedBlindingCandidateBeforeCreatingTheNextCommitment() {
        InMemoryCommitmentStore store = new InMemoryCommitmentStore();
        SequenceBlindingFactorSource source = new SequenceBlindingFactorSource(bytes(7), bytes(7), bytes(8));
        PedersenCommitmentService service = new PedersenCommitmentService(
                store, new CanonicalCredentialEncoder(), new CredentialSha256Hasher(), new CredentialRsaPssSigner(), source);
        UUID credentialId = UUID.fromString("714b523f-4285-41e1-8d7f-42e1d0caa001");

        AttributeCommitment first = service.commit(credentialId, "age", 21L);
        AttributeCommitment second = service.commit(credentialId, "age", 21L);

        assertFalse(Arrays.equals(first.blindingFingerprint(), second.blindingFingerprint()));
        org.junit.jupiter.api.Assertions.assertEquals(3, source.requests());
    }

    @Test
    void verifierRejectsSubmittedProofWhenCommittedValueIsBelowRequestedThreshold() throws Exception {
        InMemoryCommitmentStore store = new InMemoryCommitmentStore();
        CanonicalCredentialEncoder encoder = new CanonicalCredentialEncoder();
        CredentialSha256Hasher hasher = new CredentialSha256Hasher();
        CredentialRsaPssSigner issuerSigner = new CredentialRsaPssSigner();
        UUID credentialId = UUID.fromString("714b523f-4285-41e1-8d7f-42e1d0caa001");
        long value = 10L;
        Scalar blinding = Utils.scalar(9L);
        PedersenCommitment pedersen = PedersenCommitment.getDefault();
        byte[] commitmentBytes = pedersen.commit(Utils.scalar(value), blinding).toByteArray();
        byte[] attestationPayload = encoder.encodeCommitmentAttestation(credentialId, "age", commitmentBytes);
        store.save(new AttributeCommitment(
                UUID.randomUUID(), credentialId, "age", commitmentBytes,
                issuerSigner.sign(hasher.hash(attestationPayload)), attestationPayload,
                hasher.hash(blinding.toByteArray())));

        byte[] nonce = new byte[] {1, 2, 3, 4};
        CredentialPredicateProver prover = new CredentialPredicateProver(pedersen, new BulletProofGenerators(128, 1));
        byte[] proofForMinimumZero = prover.prove(value, blinding, 0L, nonce);
        CredentialPredicateVerifier verifier = new CredentialPredicateVerifier(
                store, encoder, hasher, issuerSigner, ignored -> true);

        CredentialPredicateVerificationResult result = verifier.verify(
                CredentialPredicateProver.PROOF_VERSION, credentialId, "age", 18L, nonce, proofForMinimumZero);

        assertFalse(result.verified());
    }

    private static byte[] bytes(int finalByte) {
        byte[] value = new byte[32];
        value[31] = (byte) finalByte;
        return value;
    }

    private static final class InMemoryCommitmentStore implements CommitmentStore {
        private final List<AttributeCommitment> commitments = new ArrayList<>();

        @Override
        public boolean blindingFingerprintExists(byte[] fingerprint) {
            return commitments.stream().anyMatch(commitment ->
                    java.util.Arrays.equals(commitment.blindingFingerprint(), fingerprint));
        }

        @Override
        public void save(AttributeCommitment commitment) {
            commitments.add(commitment);
        }

        @Override
        public Optional<AttributeCommitment> findLatestByCredentialIdAndAttributeName(
                UUID credentialId, String attributeName) {
            return commitments.stream()
                    .filter(commitment -> commitment.credentialId().equals(credentialId)
                            && commitment.attributeName().equals(attributeName))
                    .reduce((first, second) -> second);
        }
    }

    private static final class SequenceBlindingFactorSource implements BlindingFactorSource {
        private final List<byte[]> values;
        private int position;

        private SequenceBlindingFactorSource(byte[]... values) {
            this.values = List.of(values);
        }

        @Override
        public void nextBytes(byte[] destination) {
            System.arraycopy(values.get(position++), 0, destination, 0, destination.length);
        }

        private int requests() {
            return position;
        }
    }
}
