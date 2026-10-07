# Attribute Privacy & Commitment Threat Model (Phase 5)

## 1. Threat Profile & Boundaries

The attribute commitment subsystem safeguards sensitive personal identifiers (e.g., age, income, credit rating, test scores) against unauthorized disclosure during proving and verification.

### 1.1 Invariants & Non-Negotiable Constraints
1. **Zero Blinding Factor Persistence:**
   Raw blinding factors ($r$) MUST NEVER be written to the database, logged in application trace files, included in audit event payloads, or transmitted across verifier endpoints.
2. **Deterministic Attestation Binding:**
   The issuer's attestation signature MUST bind the exact `credentialId`, `attributeName`, and commitment point `C`. Swapping a commitment from another credential or another attribute of the same credential MUST fail attestation verification.
3. **Fail-Closed Verifier Lookup:**
   The verifier MUST resolve the commitment point directly from the trusted `CommitmentStore` by `(credentialId, attributeName)`. Verifiers MUST NEVER accept caller-supplied or client-asserted commitment points.
4. **Key Purpose Separation:**
   Attestation signatures MUST be authorized strictly under `KeyPurpose.COMMITMENT_ATTESTATION_SIGNING`. Digital signatures created under `CREDENTIAL_SIGNING`, `DOCUMENT_SIGNING`, or `JWT_SIGNING` are rejected.

---

## 2. Threat Analysis & Mitigations

| Threat | Attack Vector | Mitigation |
| :--- | :--- | :--- |
| **Commitment Substitution** | Adversary replaces commitment point $C$ with a commitment to an arbitrary forged value. | Verifier checks issuer's RSASSA-PSS attestation signature over canonical attestation payload binding the commitment to `(credentialId, attributeName)`. |
| **Cross-Credential Replay** | Prover reuses a valid commitment from Credential A to verify against Credential B. | Attestation payload explicitly embeds `credentialId` in its signed preimage. Verifier confirms ID match. |
| **Blinding Factor Leakage** | Blinding factor leaked via exception stack trace or audit table. | `AttributeOpeningSecret.toString()` redacts blinding bytes. Database schema stores only `blinding_fingerprint` (SHA-256 hash). |
| **Rainbow Table on Commitments** | Attacker tries brute-forcing low-entropy attributes (e.g. age between 18 and 65). | Each commitment uses a 256-bit cryptographically secure random blinding scalar $r \leftarrow \mathbb{Z}_q$. Information-theoretic hiding prevents brute-forcing. |
| **Key Compromise** | An issuer attestation private key is compromised. | Key is revoked in `KeyRegistryService`. `verifyHistorical` immediately rejects all signatures signed by the revoked key. |
