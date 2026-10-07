# Authenticated Attribute Commitments Architecture (Phase 5)

## 1. Overview & Mathematical Definition

In the Zero-Knowledge Document Verification Platform, authenticated credentials contain attributes that holders may need to selectively disclose or prove predicates about without revealing the underlying raw values.

Phase 5 establishes **Authenticated Attribute Commitments** based on Pedersen commitments over the Ristretto group (Curve25519) combined with issuer digital signatures under explicit key purpose authorization.

### 1.1 The Pedersen Commitment Scheme
For an integer attribute value $v \in [0, 2^{64}-1]$:
$$C = v \cdot G + r \cdot H$$
where:
- $G, H \in \mathbb{G}$ are orthogonal group generators where the discrete logarithm $\log_G(H)$ is computationally infeasible to discover (Curve25519 / Ristretto255 group).
- $v \in \mathbb{Z}_q$ is the integer attribute value.
- $r \in \mathbb{Z}_q$ is a cryptographically secure 256-bit random scalar (the blinding factor).
- $C \in \mathbb{G}$ is the resulting 32-byte compressed elliptic curve commitment point.

### 1.2 Cryptographic Properties
1. **Information-Theoretic Hiding:**
   Given the commitment $C$, it reveals zero information about the underlying value $v$. For every possible candidate value $v'$, there exists a scalar $r'$ such that $C = v'G + r'H$.
2. **Computational Binding:**
   Assuming the hardness of the discrete logarithm problem in $\mathbb{G}$, it is computationally infeasible for a prover or holder to find two pairs $(v_1, r_1) \neq (v_2, r_2)$ such that:
   $$v_1 G + r_1 H = v_2 G + r_2 H$$

---

## 2. Issuer Attestation & Cryptographic Trust Chain

A raw commitment point $C$ alone does not prove that the commitment represents a genuine credential attribute issued by an authorized authority. Phase 5 binds the commitment to the credential trust chain:

```text
Credential Attributes (v)
       ↓
Pedersen Commitment: C = vG + rH
       ↓
Attestation Canonical Payload:
  MAGIC ("ZKCA") || Version (1) || Credential UUID || AttributeName || CommitmentBytes
       ↓
SHA-256 Digest
       ↓
RSASSA-PSS Digital Signature under KeyPurpose.COMMITMENT_ATTESTATION_SIGNING
       ↓
AttributeCommitment Public Record (Persisted in Database)
  + AttributeOpeningSecret (Ephemeral, Holder-Custody Only)
```

### 2.1 Separation of Public Commitment and Secret Opening
- **Public Record (`AttributeCommitment`):**
  Contains `credentialId`, `attributeName`, `commitment` point, `attestation` signature, `attestationPayload`, `blindingFingerprint`, and `attestationKeyId`. Stored in the database and accessible for verifier lookups.
- **Holder Secret Opening (`AttributeOpeningSecret`):**
  Contains `attributeValue`, `blindingFactor` ($r$), and `blindingFingerprint`. Returned strictly during issuance to the credential holder. **NEVER stored in the database, logged, or exposed across public endpoints.**

---

## 3. Key Registry Integration

Attestation signing is mediated through `KeyRegistryService`:
- Uses explicit purpose: `KeyPurpose.COMMITMENT_ATTESTATION_SIGNING`.
- Supports key rotation: historical commitment attestations remain verifiable under `RETIRED` keys.
- Enforces fail-closed semantics: revoked keys (`REVOKED`) immediately invalidate commitment attestations.
- Purpose isolation prevents `CREDENTIAL_SIGNING` or `JWT_SIGNING` keys from being confused or reused for commitment attestations.
