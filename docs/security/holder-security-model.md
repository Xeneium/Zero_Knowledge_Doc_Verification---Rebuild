# Holder Proving Material Security & Ownership Model (Phase 6A)

## 1. Threat Profile & Boundaries

The Holder subsystem ensures that only the authorized holder can access their credentials and proving metadata, and that zero proving secrets are leaked through APIs, logs, or databases.

### 1.1 Invariants
1. **Object-Level Authorization & IDOR Protection:**
   Holder A cannot view, provision, or revoke Holder B's credentials. Access queries strictly evaluate caller ownership against `holderId`.
2. **Zero Plaintext Blinding Factor Storage:**
   The server database stores only `blinding_fingerprint` (SHA-256 hash). The opening secret $r$ remains in holder custody via `HolderProvingMaterialStore`.
3. **Role-Based Access Control (RBAC):**
   Only principals with `ROLE_HOLDER` or `ROLE_ADMIN` can access `/api/holder/**` endpoints. Verifiers and unauthenticated users are rejected.
4. **Digest Binding & Continuity:**
   The holder credential binds to the authoritative credential's canonical `credentialDigest`. If the credential is modified, the binding is broken.

---

## 2. Threat Analysis & Mitigations

| Threat | Attack Vector | Mitigation |
| :--- | :--- | :--- |
| **Insecure Direct Object Reference (IDOR)** | Holder A passes Holder B's `holderCredentialId` to `GET /api/holder/credentials/{id}`. | `HolderCredentialService.findByIdAndHolder` enforces caller ownership match. Fails closed with 404/denied. |
| **Unauthorized Provisioning** | Malicious user attempts to claim another party's unassigned credential. | Provisioning resolves the caller's identity strictly from `Authentication.getName()` / JWT subject. |
| **Witness / Blinding Factor Leakage** | Holder credential API returns blinding factors in response DTO. | `HolderCredentialResponse` explicitly omits secret fields, returning only public metadata and hex digest. |
| **Cross-Role Privilege Escalation** | Verifier attempts to invoke holder provisioning or revocation. | Spring Security filter chain restricts `/api/holder/**` to `hasAnyRole('HOLDER', 'ADMIN')`. |
| **Duplicate Provisioning Race Condition** | Concurrent provisioning requests create multiple active holder links. | Database schema enforces `UNIQUE (holder_id, credential_id)` with transactional idempotent retrieval. |
