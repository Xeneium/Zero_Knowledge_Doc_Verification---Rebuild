# Zero-Knowledge Document Verification Platform
## Cryptographic Architecture & Trust Chain Specification

**Document Version:** 1.0.0  
**Status:** Approved Architectural Baseline  
**Phase:** Phase 0 — Architecture & Security Baseline  

---

### 1. End-to-End Cryptographic Trust Chain

The platform enforces a strict, unbroken cryptographic trust chain. No verification path is valid unless every link in the chain is mathematically and semantically proven.

```
+-------------------------------------------------------------------------+
|                                 Issuer                                  |
|                 Controls RSA-PSS Private Key (Key ID: K_iss)            |
+-------------------------------------------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                          Canonical Credential                           |
|       Deterministic Versioned Binary Encoding (RFC 8785 / Custom V1)    |
+-------------------------------------------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                       SHA-256 Credential Digest                         |
|                           d = SHA-256(bytes)                            |
+-------------------------------------------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                      RSA-PSS Credential Signature                       |
|           sig = Sign_PSS(K_iss, d) [MGF1-SHA256, Salt=32 bytes]         |
+-------------------------------------------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                        Authenticated Credential                         |
|               Binds Issuer Identity, Metadata, Attributes               |
+-------------------------------------------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                         Authenticated Attribute                         |
|                     Numeric Attribute: (Name, Value v)                  |
+-------------------------------------------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                      Ristretto/Pedersen Commitment                      |
|                     C = v*G + r*H  (on Ristretto255)                     |
+-------------------------------------------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                  Issuer-Signed Commitment Attestation                   |
|       Attestation Binds: (credId, credDigest, attrName, C, versions)     |
+-------------------------------------------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|               Holder-Controlled Private Proving Material                |
|                    Private Witness: (Value v, Blinder r)                |
+-------------------------------------------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                           Predicate Statement                           |
|           Statement: v >= T  <===>  (v - T) in [0, 2^64 - 1]            |
|           Shifted Commitment: C' = C - T*G = (v - T)*G + r*H            |
+-------------------------------------------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                        Bulletproof Range Proof                          |
|         Proof over C' with Transcript Binding (Nonce, Session, Context)  |
+-------------------------------------------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                                Verifier                                 |
|       Independently verifies:                                           |
|       1. Issuer signature on credential                                 |
|       2. Issuer signature on commitment attestation                     |
|       3. Bulletproof range proof on C' with bound Nonce                 |
+-------------------------------------------------------------------------+
```

---

### 2. Precise Semantics of Cryptographic Bindings

#### 2.1. Credential Digest & Signature
* **What is Authenticated:** The exact canonical representation of all credential metadata, issuer ID, timestamps, attribute keys, and attribute values.
* **Guarantee:** Guarantees non-repudiation by the issuer and tamper-evidence for the complete credential contents.

#### 2.2. Pedersen Commitment on Attribute
* **What is Committed:** A single numeric integer attribute $v \in [0, 2^{64}-1]$ using a cryptographically secure random blinding factor $r \in \mathbb{Z}_q$.
* **Form:** $C = vG + rH$, where $G, H$ are orthogonal generators on the prime-order Ristretto255 group.
* **Guarantee:** Information-theoretic hiding (the commitment reveals zero information about $v$) and computational binding (under the discrete logarithm assumption, the prover cannot open $C$ to any value other than $v$).

#### 2.3. Issuer-Signed Commitment Attestation
* **Critical Protection:** To prevent the **"Unrelated Commitment Attack"** (where a malicious prover submits an independently created commitment to a fraudulent value and proves a range proof over it), the issuer **must sign an attestation** explicitly binding the commitment to the authenticated credential.
* **Attestation Payload:**
  $$\text{Payload} = \text{credentialId} \parallel \text{credentialDigest} \parallel \text{issuerId} \parallel \text{issuerKeyId} \parallel \text{attributeName} \parallel \text{commitmentBytes} \parallel \text{version}$$
* **Rule:** A verifier **never** accepts a caller-supplied commitment. The verifier queries the database for the issuer-attested commitment registered for the requested `credentialId` and `attributeName`.

---

### 3. Document Cryptography Specification

#### 3.1. Hashing Specification
* **Algorithm:** SHA-256 (FIPS 180-4).
* **Input:** Raw binary byte stream of the uploaded document, read directly from input stream.
* **Output:** Deterministic 32-byte digest (256 bits).
* **Storage Representation:** Hexadecimal string (lowercase, 64 characters) or raw `BYTEA` in PostgreSQL.

#### 3.2. Digital Signature Specification (RSA-PSS)
* **Algorithm:** RSASSA-PSS (PKCS #1 v2.2 / RFC 8017).
* **Key Size:** RSA 2048-bit minimum (RSA 4096-bit recommended for root issuers).
* **Digest Algorithm:** SHA-256.
* **Mask Generation Function (MGF):** MGF1 with SHA-256 (`MGF1ParameterSpec.SHA256`).
* **Salt Length:** 32 bytes (matching SHA-256 output length, per BSI TR-02102-1 and NIST SP 800-56B Rev. 2).
* **Trailer Field:** `1` (`0xBC`, standard trailer).
* **Output Encoding:** Raw byte array encoded as Base64 (standard RFC 4648 without padding breaks) for API DTOs, `BYTEA` for storage.

#### 3.3. Prohibited Cryptographic Anti-Patterns
* **No PKCS#1 v1.5 Padding:** RSASSA-PKCS1-v1_5 is strictly forbidden due to known Bleichenbacher padding oracle vulnerabilities.
* **No Implicit Character Sets:** Strings must be explicitly transcoded to bytes using standard `StandardCharsets.UTF_8`. Platform-default encoding is forbidden.
* **No Mutable State:** Cryptographic objects (digests, signers) must be created per operation or safely leased from thread-safe factories.
