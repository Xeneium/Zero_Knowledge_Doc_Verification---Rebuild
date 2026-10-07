package com.zkdoc.zkp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weavechain.ec.Scalar;
import com.weavechain.zk.bulletproofs.BulletProofGenerators;
import com.weavechain.zk.bulletproofs.PedersenCommitment;
import com.weavechain.zk.bulletproofs.Utils;
import com.zkdoc.credential.CanonicalCredentialEncoder;
import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.credential.CredentialSha256Hasher;
import com.zkdoc.keys.DevelopmentSigningKeyProvider;
import com.zkdoc.keys.KeyOwnerType;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistry;
import com.zkdoc.keys.KeyRegistryService;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PedersenCommitmentServiceTest {

    @Test
    @DisplayName("Same value with different blindings produces different commitment points")
    void sameValueWithDifferentBlindingsProducesDifferentCommitmentBytes() {
        InMemoryCommitmentStore store = new InMemoryCommitmentStore();
        PedersenCommitmentService service = new PedersenCommitmentService(
                store, new CanonicalCredentialEncoder(), new CredentialSha256Hasher(), new CredentialRsaPssSigner(),
                new SequenceBlindingFactorSource(bytes(1), bytes(2)));
        UUID credentialId = UUID.fromString("714b523f-4285-41e1-8d7f-42e1d0caa001");

        AttributeCommitment first = service.commit(credentialId, "age", 21L);
        AttributeCommitment second = service.commit(credentialId, "age", 21L);

        assertFalse(Arrays.equals(first.commitment(), second.commitment()));
    }

    @Test
    @DisplayName("Rejects repeated blinding candidates before creating next commitment")
    void rejectsARepeatedBlindingCandidateBeforeCreatingTheNextCommitment() {
        InMemoryCommitmentStore store = new InMemoryCommitmentStore();
        SequenceBlindingFactorSource source = new SequenceBlindingFactorSource(bytes(7), bytes(7), bytes(8));
        PedersenCommitmentService service = new PedersenCommitmentService(
                store, new CanonicalCredentialEncoder(), new CredentialSha256Hasher(), new CredentialRsaPssSigner(), source);
        UUID credentialId = UUID.fromString("714b523f-4285-41e1-8d7f-42e1d0caa001");

        AttributeCommitment first = service.commit(credentialId, "age", 21L);
        AttributeCommitment second = service.commit(credentialId, "age", 21L);

        assertFalse(Arrays.equals(first.blindingFingerprint(), second.blindingFingerprint()));
        assertEquals(3, source.requests());
    }

    @Test
    @DisplayName("commitWithOpening returns valid secret opening that mathematically opens C = vG + rH")
    void commitWithOpeningMathematicallyOpensCorrectly() {
        InMemoryCommitmentStore store = new InMemoryCommitmentStore();
        PedersenCommitmentService service = new PedersenCommitmentService(
                store, new CanonicalCredentialEncoder(), new CredentialSha256Hasher(), new CredentialRsaPssSigner(),
                new SequenceBlindingFactorSource(bytes(5)));
        UUID credentialId = UUID.fromString("714b523f-4285-41e1-8d7f-42e1d0caa001");
        long committedAge = 25L;

        AttributeCommitmentResult result = service.commitWithOpening(credentialId, "age", committedAge, null);
        assertNotNull(result.commitment());
        assertNotNull(result.openingSecret());
        assertEquals(committedAge, result.openingSecret().attributeValue());

        // Verify opening matches committed point
        assertTrue(service.verifyOpening(
                committedAge, result.openingSecret().blindingFactor(), result.commitment().commitment()));

        // Purporting different value must fail opening
        assertFalse(service.verifyOpening(
                26L, result.openingSecret().blindingFactor(), result.commitment().commitment()));

        // Tampered blinding must fail opening
        byte[] tamperedBlinding = result.openingSecret().blindingFactor().clone();
        tamperedBlinding[0] ^= 1;
        assertFalse(service.verifyOpening(
                committedAge, tamperedBlinding, result.commitment().commitment()));
    }

    @Test
    @DisplayName("Attestation signature verifies via KeyRegistryService trust chain")
    void attestationSignsAndVerifiesViaKeyRegistry() throws Exception {
        InMemoryCommitmentStore store = new InMemoryCommitmentStore();
        InMemoryKeyRegistry keyRegistry = new InMemoryKeyRegistry();
        DevelopmentSigningKeyProvider keyProvider = new DevelopmentSigningKeyProvider();
        KeyRegistryService keyService = new KeyRegistryService(keyRegistry, keyProvider);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair attestationKeyPair = kpg.generateKeyPair();
        String attestationKeyId = "issuer-attest-key-1";
        String issuerOwnerId = "gov-issuer-dept";

        RegisteredKey registeredKey = RegisteredKey.createActive(
                attestationKeyId, KeyPurpose.COMMITMENT_ATTESTATION_SIGNING, issuerOwnerId,
                KeyOwnerType.ISSUER, attestationKeyPair.getPublic());
        keyService.register(registeredKey);
        keyProvider.registerKey(
                attestationKeyId, KeyPurpose.COMMITMENT_ATTESTATION_SIGNING,
                attestationKeyPair.getPrivate(), attestationKeyPair.getPublic());

        PedersenCommitmentService service = new PedersenCommitmentService(
                store, new CanonicalCredentialEncoder(), new CredentialSha256Hasher(), null,
                new SequenceBlindingFactorSource(bytes(12)), keyService);

        UUID credentialId = UUID.randomUUID();
        AttributeCommitmentResult result = service.commitWithOpening(credentialId, "salary", 85000L, issuerOwnerId);

        // Verification of attestation must succeed
        assertTrue(service.verifyAttestation(result.commitment(), issuerOwnerId));

        // Tampering attribute name in payload must fail verification
        byte[] tamperedPayload = result.commitment().attestationPayload().clone();
        tamperedPayload[0] ^= 1;
        AttributeCommitment tamperedCommitment = new AttributeCommitment(
                result.commitment().id(), result.commitment().credentialId(), result.commitment().attributeName(),
                result.commitment().commitment(), result.commitment().attestation(), tamperedPayload,
                result.commitment().blindingFingerprint(), result.commitment().attestationKeyId());
        assertFalse(service.verifyAttestation(tamperedCommitment, issuerOwnerId));

        // Tampering attestation signature must fail verification
        byte[] tamperedSig = result.commitment().attestation().clone();
        tamperedSig[0] ^= 1;
        AttributeCommitment tamperedSigCommitment = new AttributeCommitment(
                result.commitment().id(), result.commitment().credentialId(), result.commitment().attributeName(),
                result.commitment().commitment(), tamperedSig, result.commitment().attestationPayload(),
                result.commitment().blindingFingerprint(), result.commitment().attestationKeyId());
        assertFalse(service.verifyAttestation(tamperedSigCommitment, issuerOwnerId));
    }

    @Test
    @DisplayName("Key rotation and revocation lifecycle on attestation signing keys")
    void keyLifecycleOnAttestationKeys() throws Exception {
        InMemoryCommitmentStore store = new InMemoryCommitmentStore();
        InMemoryKeyRegistry keyRegistry = new InMemoryKeyRegistry();
        DevelopmentSigningKeyProvider keyProvider = new DevelopmentSigningKeyProvider();
        KeyRegistryService keyService = new KeyRegistryService(keyRegistry, keyProvider);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPairA = kpg.generateKeyPair();
        KeyPair keyPairB = kpg.generateKeyPair();

        String keyAId = "attest-key-a";
        String keyBId = "attest-key-b";
        String issuer = "issuer-corp";

        // Register Key A
        keyService.register(RegisteredKey.createActive(
                keyAId, KeyPurpose.COMMITMENT_ATTESTATION_SIGNING, issuer, KeyOwnerType.ISSUER, keyPairA.getPublic()));
        keyProvider.registerKey(keyAId, KeyPurpose.COMMITMENT_ATTESTATION_SIGNING, keyPairA.getPrivate(), keyPairA.getPublic());

        PedersenCommitmentService service = new PedersenCommitmentService(
                store, new CanonicalCredentialEncoder(), new CredentialSha256Hasher(), null,
                new SequenceBlindingFactorSource(bytes(15), bytes(16)), keyService);

        UUID credId = UUID.randomUUID();
        AttributeCommitmentResult commitA = service.commitWithOpening(credId, "age", 30L, issuer);
        assertTrue(service.verifyAttestation(commitA.commitment(), issuer));

        // Rotate Key A -> Key B
        keyService.register(RegisteredKey.createActive(
                keyBId, KeyPurpose.COMMITMENT_ATTESTATION_SIGNING, issuer, KeyOwnerType.ISSUER, keyPairB.getPublic()));
        keyProvider.registerKey(keyBId, KeyPurpose.COMMITMENT_ATTESTATION_SIGNING, keyPairB.getPrivate(), keyPairB.getPublic());
        keyService.rotateKey(keyAId, keyBId);

        // Historical commitment under Key A must still verify
        assertTrue(service.verifyAttestation(commitA.commitment(), issuer));

        // New commitment signed under new active Key B
        AttributeCommitmentResult commitB = service.commitWithOpening(credId, "score", 750L, issuer);
        assertEquals(keyBId, commitB.commitment().attestationKeyId());
        assertTrue(service.verifyAttestation(commitB.commitment(), issuer));

        // Revoking Key A fails historical verification closed
        keyService.revokeKey(keyAId, "Suspected compromise");
        assertFalse(service.verifyAttestation(commitA.commitment(), issuer));

        // Key B commitment remains unaffected and valid
        assertTrue(service.verifyAttestation(commitB.commitment(), issuer));
    }

    @Test
    @DisplayName("Verifier rejects submitted proof when committed value is below requested threshold")
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
                hasher.hash(blinding.toByteArray()), issuerSigner.getKeyId()));

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
                    Arrays.equals(commitment.blindingFingerprint(), fingerprint));
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

        @Override
        public Optional<AttributeCommitment> findByCredentialIdAndAttributeName(UUID credentialId, String attributeName) {
            return findLatestByCredentialIdAndAttributeName(credentialId, attributeName);
        }

        @Override
        public Optional<AttributeCommitment> findById(UUID commitmentId) {
            return commitments.stream()
                    .filter(c -> c.id().equals(commitmentId))
                    .findFirst();
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

    private static class InMemoryKeyRegistry implements KeyRegistry {
        private final List<RegisteredKey> keys = new ArrayList<>();

        @Override
        public Optional<RegisteredKey> findByKeyId(String keyId) {
            return keys.stream().filter(k -> k.keyId().equals(keyId)).findFirst();
        }

        @Override
        public Optional<RegisteredKey> findForVerification(String keyId, KeyPurpose purpose) {
            return keys.stream().filter(k -> k.keyId().equals(keyId) && k.purpose() == purpose).findFirst();
        }

        @Override
        public Optional<RegisteredKey> findForVerification(String keyId, KeyPurpose purpose, String expectedOwnerId) {
            return keys.stream().filter(k -> k.keyId().equals(keyId) && k.purpose() == purpose && k.ownerId().equals(expectedOwnerId)).findFirst();
        }

        @Override
        public Optional<RegisteredKey> findActiveForSigning(KeyPurpose purpose) {
            return keys.stream().filter(k -> k.purpose() == purpose && k.status() == KeyStatus.ACTIVE).findFirst();
        }

        @Override
        public Optional<RegisteredKey> findActiveForSigning(KeyPurpose purpose, String ownerId) {
            return keys.stream().filter(k -> k.purpose() == purpose && k.ownerId().equals(ownerId) && k.status() == KeyStatus.ACTIVE).findFirst();
        }

        @Override
        public List<RegisteredKey> findAllByOwner(String ownerId) {
            return keys.stream().filter(k -> k.ownerId().equals(ownerId)).toList();
        }

        @Override
        public void register(RegisteredKey key) {
            keys.add(key);
        }

        @Override
        public void updateStatus(String keyId, KeyStatus status, String revocationReason, String replacementKeyId) {
            for (int i = 0; i < keys.size(); i++) {
                RegisteredKey k = keys.get(i);
                if (k.keyId().equals(keyId)) {
                    keys.set(i, new RegisteredKey(
                            k.id(), k.keyId(), k.purpose(), status, k.algorithm(), k.algorithmParameters(),
                            k.ownerId(), k.ownerType(), k.publicKeyPem(), k.publicKeyDer(), k.publicKeyEncoding(),
                            k.fingerprint(), k.createdAt(), k.activatedAt(),
                            status == KeyStatus.RETIRED ? Instant.now() : k.retiredAt(),
                            status == KeyStatus.REVOKED ? Instant.now() : k.revokedAt(),
                            revocationReason, replacementKeyId, k.version() + 1, k.metadata()));
                    return;
                }
            }
        }

        @Override
        public void rotateKey(String oldKeyId, String newKeyId) {
            updateStatus(oldKeyId, KeyStatus.RETIRED, "Rotated to " + newKeyId, newKeyId);
        }
    }
}
