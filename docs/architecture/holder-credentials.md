# Holder Credential & Proving-Material Provisioning Architecture (Phase 6A)

## 1. Overview & Architectural Boundaries

Phase 6A establishes the **holder-centric domain model** and **proving-material provisioning boundary** for the Zero-Knowledge Document Verification Platform.

A core invariant of the platform is:
> The verifier platform must not become the long-term custodian of the holder's private ZKP witness material merely because it issued the credential.

```text
Trusted Issuer
      ↓
Authoritative Issued Credential (canonical_payload, RSA-PSS signature)
      ↓
Authenticated Attribute Commitment (C = vG + rH, signed attestation)
      ↓
Holder Provisioning Workflow (subject-authenticated, idempotent)
      ↓
Holder Credential Reference (holder_id, credential_id, credential_digest)
      ↓
Holder Private Proving Material Store (holder custody of v and r)
```

---

## 2. Issued Credential vs. Holder Credential

| Property | Issued Credential (`Credential`) | Holder Credential (`HolderCredential`) |
| :--- | :--- | :--- |
| **Authority** | The issuing authority (Issuer). | The credential subject/holder. |
| **Purpose** | Authoritative assertion of claims and digital signature. | Holder's operational reference for managing credentials and proving predicates. |
| **Payload** | Full canonical binary payload, attributes, issuer signature. | Minimal reference: `holderId`, `credentialId`, and `credentialDigest`. |
| **Lifecycle** | Governed by issuer authority and expiry (`ACTIVE`, `SUSPENDED`, `REVOKED`, `EXPIRED`). | Governed by holder control and operational state (`ACTIVE`, `SUSPENDED`, `REVOKED`, `EXPIRED`). |

---

## 3. Separation of Public vs. Holder-Private Data

### 3.1 Public & Server-Side Persisted Metadata
The platform database and public APIs store only non-secret cryptographic metadata:
- `credentialId` (UUID)
- `credentialDigest` (SHA-256 hash)
- `holderId` (Subject)
- `status` and `version`
- `commitmentPoint` ($C \in \mathbb{G}$)
- `attestationSignature` (RSASSA-PSS signature)
- `blindingFingerprint` (SHA-256 hash of $r$)

### 3.2 Holder-Private Material
The following data is strictly holder-custodied:
- `attributeValue` ($v$)
- `blindingFactor` ($r \leftarrow \mathbb{Z}_q$)
- Opening secret material ($r, v$)
- Future ZKP witnesses

**Zero Server Custody Rule:**
Blinding factors and raw attribute opening secrets are **NEVER** stored in relational tables, emitted in application logs, included in audit events, or exposed via verifier endpoints.

---

## 4. Holder Proving Material SPI (`HolderProvingMaterialStore`)

The platform defines an explicit SPI abstraction [`HolderProvingMaterialStore`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-zkp/src/main/java/com/zkdoc/zkp/HolderProvingMaterialStore.java) to isolate private proving material:
- Scoped strictly by `(holderId, credentialId, attributeName)`.
- Replaced in enterprise deployments with client-side encrypted wallets, secure enclaves, or hardware keys.
- Completely inaccessible to verifier endpoints and general administrative operators.
