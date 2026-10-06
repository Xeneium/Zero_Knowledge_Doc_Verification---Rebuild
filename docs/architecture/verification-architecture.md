# Zero-Knowledge Document Verification Platform
## Verification Architecture & QR Verification Modes

**Document Version:** 1.0.0  
**Status:** Approved Architectural Baseline  
**Phase:** Phase 0 — Architecture & Security Baseline  

---

### 1. Structured Multi-Dimensional Verification Model

A single `boolean isValid` response is insufficient for enterprise auditing, security monitoring, and debugging. The platform establishes a **Structured Verification Model** that isolates and reports across explicit verification dimensions:

```java
public record VerificationResult(
    boolean overallValid,
    String verificationCode,          // Machine-readable code (e.g., "VALID", "SIGNATURE_INVALID")
    Map<VerificationCheck, Boolean> checks,
    Instant verifiedAt,
    String correlationId
) {}
```

#### 1.1. Evaluated Dimensions

| Dimension | Description | Public Visibility | Verifier Visibility | Audit Visibility |
| :--- | :--- | :--- | :--- | :--- |
| `REQUEST_VALID` | Syntax, size bounds, and nonces are valid | Yes | Yes | Yes |
| `CAPABILITY_VALID` | QR token or session token is valid and unexpired | Yes | Yes | Yes |
| `RECORD_EXISTS` | Document or credential exists in database | Masked | Yes | Yes |
| `LIFECYCLE_ACTIVE` | Document/credential status is `ACTIVE` (not suspended/revoked) | Yes (`documentActive`) | Yes | Yes |
| `EXPIRATION_VALID` | Current time < `expiresAt` | Masked | Yes | Yes |
| `ISSUER_TRUSTED` | Key is registered and owned by recognized issuer | Masked | Yes | Yes |
| `KEY_STATUS_VALID` | Signing key is `ACTIVE` or `RETIRED` (not `REVOKED`) | Masked | Yes | Yes |
| `SIGNATURE_VALID` | RSA-PSS signature verifies against computed digest | Yes (`signatureValid`) | Yes | Yes |
| `COMMITMENT_AUTH` | Issuer-signed commitment attestation is valid | No (ZKP only) | Yes | Yes |
| `ATTR_BOUND` | Commitment is bound to requested attribute name | No (ZKP only) | Yes | Yes |
| `PROOF_VALID` | Range proof mathematically verifies over $C'$ | No (ZKP only) | Yes | Yes |
| `PREDICATE_SATISFIED` | Proved range confirms $v \ge \text{threshold}$ | No (ZKP only) | Yes | Yes |
| `REPLAY_ABSENT` | Nonce is fresh; no session replay detected | Masked | Yes | Yes |

* **Data Minimization in Public API:** The unauthenticated endpoint `GET /api/public/verify/{token}` returns **only**:
  ```json
  {
    "signatureValid": true,
    "documentActive": true
  }
  ```
  It reveals no IDs, no file names, no hashes, and no internal check details.

---

### 2. Dual-Mode Document Verification

The platform explicitly separates document verification into two distinct operational flows with different threat profiles and performance characteristics:

```mermaid
flowchart TD
    subgraph MODE_A ["Mode A: Registered Artifact Verification (QR Token)"]
        Scanner["Camera Scans QR"] -->|Read URL| Token["Extract 256-bit Opaque Token"]
        Token -->|GET /api/public/verify/{token}| PubAPI["Public Verification Controller"]
        PubAPI -->|Lookup by Token| Metadata["Fetch Registered Record (Digest + Sig)"]
        Metadata -->|Verify RSA-PSS with KeyRegistry| ResultA["Return {signatureValid, documentActive}"]
        NoteA["ZERO byte I/O from Document Blob Store"]
    end

    subgraph MODE_B ["Mode B: Presented Artifact Verification (Independent File Upload)"]
        UserFile["Verifier Uploads Physical File"] -->|POST /api/documents/{id}/verify-upload| DocAPI["Document Controller"]
        DocAPI -->|Stream Hashing| CalcHash["Compute SHA-256 Digest of Uploaded Bytes"]
        CalcHash -->|Compare| MatchCheck{"Matches Registered Digest?"}
        MatchCheck -->|No| FailB["Return Tampering Detected (400)"]
        MatchCheck -->|Yes| SigCheck["Verify Stored RSA-PSS Signature"]
        SigCheck --> ResultB["Return Full Verification Confirmation"]
    end
```

#### 2.1. Mode A: Registered Artifact Verification (Fast / Zero-Storage I/O)
* **Trigger:** An inspector scans a QR code affixed to a document or badge.
* **Mechanism:** The QR encodes a capability URL with an opaque 256-bit random token.
* **Verification Logic:**
  1. Platform queries database index for the token.
  2. Resolves stored SHA-256 digest, stored RSA-PSS signature, and signing key ID.
  3. Validates the signature over the stored digest using the registered public key.
* **Security & Performance Invariant:** The platform **does not load the document bytes** from object storage. The verification verifies the integrity of the registered cryptographic metadata.

#### 2.2. Mode B: Presented Artifact Verification (Content Match / Full I/O)
* **Trigger:** An auditor or verifier holds an independent electronic file (e.g. PDF) and wants to prove that this exact file is the authentic registered document.
* **Mechanism:** The caller uploads the raw file to `POST /api/documents/{id}/verify-upload`.
* **Verification Logic:**
  1. Platform streams the uploaded file through SHA-256 engine.
  2. The computed digest is compared using constant-time comparison against the registered digest.
  3. If digests match, the registered signature is verified against the key registry.
* **Security Guarantee:** Guarantees that the file currently presented by the verifier is bit-for-bit identical to the document authenticated by the issuer.
