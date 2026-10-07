# Cryptographic Key Management Architecture

## 1. Overview & Trust Philosophy

In the Zero-Knowledge Document Verification Platform, cryptographic keys are foundational security anchors. Ephemeral in-memory keys, hardcoded keys, and constructor key generation are strictly prohibited. Every cryptographic key adheres to an explicit lifecycle:

```
Key Identity -> Purpose -> Owner / Trust Domain -> Public Key -> Lifecycle -> Private-Key Custody -> Cryptographic Operation -> Historical Verification
```

Keys are managed through an explicit domain separation between public key trust metadata (stored in the relational `key_registry`) and private key custody (abstracted through the `SigningKeyProvider` SPI).

---

## 2. Cryptographic Algorithm & Standard

All RSA digital signatures generated or verified by the platform use **RSASSA-PSS** conforming to **RFC 8017**:

- **Signature Scheme:** RSASSA-PSS
- **Hash Function:** SHA-256 (`MessageDigest.getInstance("SHA-256")`)
- **Mask Generation Function:** MGF1 initialized with SHA-256 (`MGF1ParameterSpec.SHA256`)
- **Salt Length:** 32 bytes (matching the SHA-256 output length)
- **Trailer Field:** 1 (`PSSParameterSpec.TRAILER_FIELD_BC`)
- **Minimum Modulus Size:** 2048 bits (3072+ bits recommended for production)

Legacy PKCS#1 v1.5 padding and raw RSA signatures are strictly rejected.

---

## 3. Key Purposes & Purpose Separation

To prevent cross-protocol signature forgery and purpose confusion attacks, every key is assigned exactly one `KeyPurpose`:

1. `JWT_SIGNING`: Signs and verifies authentication and authorization tokens.
2. `CREDENTIAL_SIGNING`: Signs canonical verifiable credential binary frames.
3. `DOCUMENT_SIGNING`: Signs document content SHA-256 digests.
4. `COMMITMENT_ATTESTATION_SIGNING`: Signs Pedersen attribute commitments or cryptographic attestations.

A key registered for `DOCUMENT_SIGNING` can never be used to sign credentials, nor can a `JWT_SIGNING` key verify credential assertions. Mismatched purpose checks fail closed immediately.

---

## 4. Key Ownership & Trust Domains

Every key is bound to an explicit `KeyOwner`:

- **Owner ID:** Normalized, non-empty identifier (e.g. `platform`, `dept-registry-01`, `issuer-university-1`).
- **Owner Type:**
  - `PLATFORM`: Platform-level service or infrastructure key.
  - `ISSUER`: Accredited credential issuer.
  - `VERIFIER`: Relying party or verifier organisation.
  - `EXTERNAL`: External trusted authority or identity provider.

Trust resolution mandates matching the key's registered `ownerId` with the expected issuer or tenant domain.

---

## 5. Key Lifecycle State Machine

Keys transition through four deterministic lifecycle states:

```
+------------+       phase-out       +--------------+      decommission      +-------------+
|   ACTIVE   | --------------------> |   RETIRING   | ---------------------> |   RETIRED   |
+------------+                       +--------------+                        +-------------+
      |                                     |                                       |
      |                                     | compromise                            | compromise
      | compromise                          v                                       v
      +-----------------------------> +-------------+ <-----------------------------+
                                      |   REVOKED   | (Terminal fail-closed)
                                      +-------------+
```

### State Semantics
| State | Can Sign New Payloads? | Can Verify Historical Signatures? | Description |
|---|:---:|:---:|---|
| **ACTIVE** | Yes | Yes | Key is fully operational for signing and verification. |
| **RETIRING** | Restricted | Yes | Scheduled rotation in progress; active key replacement exists. |
| **RETIRED** | **No** | **Yes** | Decommissioned after rotation. Preserved exclusively to allow verification of historical documents and credentials signed during its active period. |
| **REVOKED** | **No** | **No** | Terminally invalidated due to private key compromise or administrative revocation. All verification attempts fail closed. |

---

## 6. Private-Key Custody & `SigningKeyProvider`

Private keys are never stored in the database, never included in logs, and never embedded in `RegisteredKey` domain records.

Private-key access is encapsulated behind the `SigningKeyProvider` interface:
```java
public interface SigningKeyProvider {
    PrivateKey getSigningKey(String keyId, KeyPurpose purpose);
    boolean isAvailable(String keyId);
}
```

- **Development/Test:** `DevelopmentSigningKeyProvider` manages keys in memory or configured secure keystores. Crucially, before executing any signing operation, it runs an internal **consistency probe**: it signs a test vector with the private key and verifies the signature using the registered public key. If the keys do not form a valid pair, it fails closed immediately.
- **Production:** Integrates with hardware security modules (HSM) or cloud KMS (HashiCorp Vault, AWS KMS, Azure Key Vault) without exposing private key bytes to the Java application runtime.

---

## 7. Deterministic Fingerprinting

Every registered public key is assigned a deterministic SHA-256 fingerprint:
$$\text{fingerprint} = \text{hex}(\text{SHA-256}(\text{SubjectPublicKeyInfo DER bytes}))$$

This ensures canonical comparison, deduplication, and indexed database lookups regardless of PEM formatting or line-ending differences.
