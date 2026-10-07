# Zero-Knowledge Document Verification Platform
## Credential Security & Immutability Analysis

**Document Version:** 1.0.0  
**Status:** Approved Architectural Baseline  
**Phase:** Phase 2 — Canonical Credential & Domain Architecture  

---

### 1. Cryptographic vs. Operational Field Classification

To protect against signature invalidation, state tampering, and side-channel exposure, every field in the credential ecosystem is explicitly categorized:

| Field | Canonicalized | Authenticated (Signed) | Mutable | Sensitive | Threat Mitigation |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `id` (Credential UUID) | **Yes** | **Yes** | **No** | No | Prevents credential ID swapping and re-binding attacks. |
| `schemaId` | **Yes** | **Yes** | **No** | No | Prevents type confusion and semantic polymorphism attacks. |
| `issuerId` | **Yes** | **Yes** | **No** | No | Prevents attribution fraud and impersonation. |
| `issuerKeyId` | **Yes** | **Yes** | **No** | No | Cryptographically binds the public key instance to the claim. |
| `issuedAt` | **Yes** | **Yes** | **No** | No | Bounds temporal validity; resists backdating. |
| `expiresAt` | **Yes** | **Yes** | **No** | No | Enforces hard expiration without relying on external revocation checks. |
| `attributes` | **Yes** | **Yes** | **No** | **Potentially** | Sorted lexicographically; prevents attribute injection and duplicate collision. |
| `version` | **Yes (in frame)**| **Yes** | **No** | No | Format header versioning binds the binary interpretation. |
| `status` (Operational) | **No** | **No** | **Yes** | No | Allows administrative revocation/suspension without breaking digital signature. |
| `created_at` (DB) | **No** | **No** | **No** | No | Internal audit timestamp; excluded from cryptographic proof. |

---

### 2. Immutability & Lifecycle Boundaries

#### 2.1. In-Memory Domain Immutability
* All domain entities are implemented using Java records or final classes.
* Collections are wrapped via `List.copyOf` upon instantiation to prevent internal mutation.
* Defensively copied byte arrays in `Digest`, `CanonicalRepresentation`, `SignedCredential`, and `Document` prevent external memory modification.

#### 2.2. Decoupling Lifecycle from Cryptographic Evidence
A critical flaw in naive verifiable credential designs is binding operational state (e.g., `ACTIVE` vs `REVOKED`) into the signed payload. When an issuer subsequently revokes a credential, modifying the status would invalidate the digital signature, making it impossible to cryptographically verify who originally issued it and what was signed.

In this platform:
* The digital signature covers only the permanent claims and validity window.
* Operational state (`ACTIVE`, `SUSPENDED`, `REVOKED`, `EXPIRED`) is tracked in the registry.
* Verification requires:
  $$\text{Verify}(\text{Credential}) = \text{VerifySignature}(\text{bytes}, \text{sig}) \land \text{IsStatusActive}(\text{id}) \land (\text{now} < \text{expiresAt})$$

---

### 3. Serialization Attack Resistance

The implementation of `CanonicalCredentialEncoder` specifically prevents the following attack vectors:

1. **Hash Flooding & Order Permutation:**
   * Attributes are sorted in strictly ascending lexicographical order by their UTF-8 byte sequences.
   * Shuffled or maliciously reordered inputs always result in bit-for-bit identical canonical frames.
2. **Duplicate Attribute Ambiguity:**
   * Input attributes with duplicate names are rejected immediately during pre-sorting validation.
3. **Unicode Homoglyph / Decomposition Exploits:**
   * All string inputs are normalized using Unicode Normalization Form C (`Normalizer.Form.NFC`) before byte encoding.
   * Prevents canonical bypasses using decomposed combining characters.
4. **Locale & Timezone Leakage:**
   * Timestamps are encoded strictly as 64-bit big-endian signed epoch milliseconds in UTC.
   * Floats and locale-dependent formatting (`String.format()`, decimal separators) are prohibited.
5. **Java Deserialization Gadgets:**
   * Java native serialization (`Serializable`, `ObjectInputStream`) is completely banned. All encoding uses raw big-endian `DataOutputStream` frames.
