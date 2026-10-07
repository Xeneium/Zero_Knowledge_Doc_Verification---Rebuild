# Phase 11: QR & Public Verification Capability Architecture

## 1. Overview & Core Security Invariant

Phase 11 establishes a secure QR-based verification-capability architecture for the Zero-Knowledge Document Verification Platform.

The core architectural invariant is:
> **"A QR code is a capability/discovery mechanism, not a container for secrets or a substitute for the server-side cryptographic trust chain."**

```text
Credential
   ↓
Authenticated Credential
   ↓
Document Digest
   ↓
Authenticated Commitment
   ↓
Holder / Proof Context
   ↓
Predicate Proof
   ↓
Verification Context
   ↓
QR Capability (Opaque Bearer Token)
   ↓
Verifier Scan / Reference Input
   ↓
Trusted Server-Side Resolution
   ↓
Cryptographic Verification
   ↓
Minimal Public Result
```

---

## 2. Explicit Verification Modes

Phase 11 strictly distinguishes two distinct verification pathways:

1. **Mode A — Presented-Artifact Verification (`MODE_A_PRESENTED_ARTIFACT`)**:
   - The verifier supplies the actual document bytes alongside the capability reference.
   - The server computes SHA-256 over the presented bytes and evaluates authenticity against the stored RSASSA-PSS digital signature.
2. **Mode B — Registry/Capability Verification (`MODE_B_REGISTRY_CAPABILITY`)**:
   - The QR identifies a server-side verification capability record referencing an authoritative credential or document.
   - Cryptographic verification resolves issuer signatures, key registry trust, and lifecycle validity without loading document content bytes.

---

## 3. Privacy & Anti-Leakage Constraints

The QR payload and visual matrix **strictly forbid** embedding:
- Raw document bytes or file contents
- Raw attribute values
- Blinding factors or opening secrets
- Witness material or private proving parameters
- Issuer private keys
- Database passwords or sensitive internal configuration
- JWT access tokens or administrative session tokens

The QR payload encodes only:
```json
{
  "v": "QR_V1",
  "m": "MODE_B_REGISTRY_CAPABILITY",
  "tok": "<256-bit secure opaque random token>",
  "uri": "https://<verifier-domain>/api/public/verify/<tok>"
}
```

---

## 4. Capability Lifecycle & Authoritative Server-Side State

Capabilities are managed under an authoritative lifecycle model in [`VerificationCapability`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-api/src/main/java/com/zkdoc/api/qr/VerificationCapability.java):
- **States**: `ACTIVE`, `EXPIRED`, `REVOKED`, `DISABLED`.
- **Database Schema**: Backed by Flyway migrations `V10__verification_capabilities.sql` (SQLite) and `V5__verification_capabilities.sql` (PostgreSQL) with unique indexes on `capability_token` and composite index on `(status, expires_at)`.
- **Authoritative Revocation**: Revoking a capability marks its server record as `REVOKED` immediately, rendering subsequent verification attempts invalid regardless of printed or cached QR images.
- **Cache-Control Security**: Endpoints serving visual QR images enforce `Cache-Control: no-store, no-cache, must-revalidate` to prevent proxy or browser disk caching of visual tokens.

---

## 5. Public Verification Minimality

The public verification endpoint (`/api/public/verify/{token}` and `/api/public/verify/capability/{token}`) enforces minimal disclosure:
- Returns only verification status (`VALID` / `INVALID`), mode, and protocol version.
- Rejects enumeration attempts via high-entropy tokens (256 bits from `SecureRandom`) and constant-time database lookup.
- Integrated with `PublicVerificationRateLimitFilter` to prevent brute-force probing and CPU exhaustion.
