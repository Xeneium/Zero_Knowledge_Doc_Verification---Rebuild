# F7: Holder Workflows Security & Privacy Review

**Project:** Zero-Knowledge Document Verification Platform  
**Phase:** Frontend Phase F7  
**Document ID:** ZKDOC-FE-F7-003  
**Status:** Approved Security Assessment  
**Date:** October 2026  

---

## 1. Executive Summary & Scope

This document provides a comprehensive application security, privacy, and cryptographic review of the Frontend Phase F7 implementation (*Holder Workflows — Secure Credential Access, Credential Management, and Privacy-Preserving Holder Experience*).

The review evaluated:
1. **Privacy Enclave & Minimal Disclosure:** Verification that zero raw attribute values, blinding factors, or opening secrets are retained in client memory, storage, or rendered DOM trees.
2. **Broken Object-Level Authorization (BOLA / IDOR):** Verification that holder-scoped operations (`/api/holder/*`) are cryptographically and contextually bound to the authenticated user's principal.
3. **Proof Replay & Cryptographic Nonce Freshness:** Verification that zero-knowledge predicate proof generation incorporates cryptographically secure, unpredictable nonces to prevent replay attacks.
4. **Resource Management & Object URL Deallocation:** Verification that binary QR image blobs are properly revoked to prevent browser memory leaks (CWE-401).
5. **Route Protection & RBAC Enforcement:** Verification that holder dashboard interfaces are protected by strict role guards (`ROLE_HOLDER` or `ROLE_ADMIN`).
6. **Input Sanitization & Boundary Validation:** Verification of UUID syntax constraints and justification reason validation prior to backend dispatch.

---

## 2. Threat Modeling & Vulnerability Matrix

| Vulnerability / Threat | CWE / OWASP ID | Pre-F7 Risk Level | F7 Mitigation & Defense Controls | Residual Risk |
| :--- | :--- | :---: | :--- | :---: |
| **Raw Attribute Disclosure** | CWE-359<br>OWASP A01:2021 | **CRITICAL** | Frontend models strictly accept public commitments and metadata. Raw secrets or witness data never enter client state or local storage. | **NONE** |
| **Holder Impersonation (BOLA/IDOR)** | CWE-639<br>OWASP A01:2021 | **HIGH** | `HolderController.java` enforces principal-to-holder binding via Spring Security context. Frontend sends no spoofable client identifiers. | **NONE** |
| **Proof Replay Attack** | CWE-294<br>OWASP A02:2021 | **HIGH** | `PredicateProofGeneratorCard` generates 16-byte cryptographically secure random nonces via `crypto.getRandomValues()` bound to proof transcripts. | **NONE** |
| **Object URL Memory Leaks** | CWE-401<br>OWASP A04:2021 | **MEDIUM** | Binary QR blobs converted via `URL.createObjectURL` are tracked and explicitly revoked via `URL.revokeObjectURL` on re-render and unmount. | **NONE** |
| **Client Memory Bloat (DoS)** | CWE-400<br>OWASP A04:2021 | **MEDIUM** | `holderStore` enforces a strict FIFO capacity limit of 20 proof artifacts (`proofCache.slice(0, 20)`). | **LOW** |
| **UUID Format Injection** | CWE-20<br>OWASP A03:2021 | **LOW** | Strict regular expression pre-flight validation blocks malformed identifiers before dispatch to `/api/holder/credentials/provision`. | **NONE** |
| **Unauthorized Route Access** | CWE-285<br>OWASP A01:2021 | **HIGH** | Protected routes `/credentials` and `/holder` wrapped in `<RequireRole allowedRoles={['HOLDER', 'ADMIN']}>`. | **NONE** |

---

## 3. In-Depth Analysis of Security Controls

### 3.1 Privacy Enclave & Zero-Witness Guarantee
A fundamental hazard in identity platforms is the unnecessary leakage of personally identifiable information (PII) to the client application:
- **No Plaintext Download:** Unlike legacy systems that allow arbitrary downloading of underlying PDF or image files to client disk, the holder wallet operates strictly on cryptographic commitments (`attributeCommitments`) and public digests.
- **Commitment Integrity:** The frontend displays cryptographic digests using `<FormattedHash>` and evaluates RSA-PSS signature authenticity via `GET /api/credentials/{id}/verify`.
- **Zero Witness Material:** For zero-knowledge range proofs, witness secrets remain protected in the backend's secure cryptographic enclave. The holder specifies only the target attribute and threshold value.

### 3.2 Cryptographic Nonce Freshness & Anti-Replay Controls
In zero-knowledge verification protocols, replaying an existing valid proof allows an adversary to impersonate a holder or satisfy a challenge without possessing the underlying credential.

To prevent replay attacks:
1. `PredicateProofGeneratorCard.tsx` enforces a fresh challenge nonce for every proof request.
2. The nonce is generated using the browser's cryptographically secure pseudo-random number generator (CSPRNG):
   ```typescript
   const array = new Uint8Array(16);
   window.crypto.getRandomValues(array);
   const freshNonce = Array.from(array, (b) => b.toString(16).padStart(2, '0')).join('');
   ```
3. Insecure random generators (such as `Math.random()`) are strictly prohibited.
4. The generated nonce is submitted within the proof request payload and bound to the Bulletproof fiat-shamir challenge transcript.

### 3.3 Object-Level Authorization Alignment
Holder operations are secured by design against IDOR vulnerabilities:
- The backend's `HolderController.java` resolves the target holder directly from the authenticated `SecurityContext`:
  ```java
  @GetMapping("/credentials")
  public ResponseEntity<List<HolderCredentialResponse>> getMyCredentials(Authentication auth) {
      String holderId = auth.getName();
      return ResponseEntity.ok(holderService.getCredentialsForHolder(holderId));
  }
  ```
- The frontend does not construct or pass arbitrary `holderId` parameters in query strings or request bodies.
- An attacker tampering with the client application cannot enumerate or query another user's credentials.

### 3.4 Safe Binary Stream & Blob Handling
When rendering capability QR codes:
1. The client requests `image/png` from `GET /api/verification/capabilities/{id}/qr`.
2. The resulting `Blob` is converted to a DOM-renderable URL via `URL.createObjectURL(blob)`.
3. To prevent memory leaks:
   ```typescript
   useEffect(() => {
     return () => {
       if (qrBlobUrl) {
         URL.revokeObjectURL(qrBlobUrl);
       }
     };
   }, [qrBlobUrl]);
   ```
4. If a new QR code is minted, the previous object URL is revoked prior to allocating the replacement URL.

### 3.5 React 19 State Discipline & Hook Safety
In `HolderCredentialDetailModal.tsx`, dynamic sub-resource fetching (signature verification and lifecycle history) was architected using a keyed sub-component pattern (`<HolderCredentialDetailBody key={credential.id} />`). This architectural choice:
- Completely eliminated `react-hooks/set-state-in-effect` violations.
- Guarantees that local fetch state and error boundaries are unmounted and re-initialized cleanly when switching between credentials.
- Precludes race conditions where verification data from credential $A$ could display under credential $B$.

---

## 4. Residual Risk & Recommendations for Phase F8

1. **Public Verifier Verification (Phase F8 Scope):**  
   While Phase F7 guarantees secure proof generation and capability token issuance by holders, Phase F8 must implement the verifier side:
   - Ensuring public QR scanner inputs validate capability signatures and time-to-live (TTL).
   - Verifying Bulletproof presentation tokens against public challenge parameters without credential owner presence.
2. **Clipboard Data Cleansing:**  
   Copying presentation tokens or capability tokens places cryptographic material on the system clipboard. Users should be notified that capability tokens grant temporary verification access and should not be shared via unencrypted public channels.
