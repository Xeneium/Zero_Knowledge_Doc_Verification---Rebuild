# F9: Administrator & Audit Security & Threat Analysis Review

**Project:** Zero-Knowledge Document Verification Platform  
**Phase:** Frontend Phase F9  
**Document ID:** ZKDOC-FE-F9-004  
**Status:** Approved Security Assessment  
**Date:** October 2026  

---

## 1. Executive Summary

Phase F9 implements the administrative and audit operational surface for the Zero-Knowledge Document Verification Platform. Because administrative features possess elevated authority over trust anchors, platform reconciliation, credential validity, and security audit logs, rigorous security boundaries, zero-trust state handling, and defensive UI safeguards were designed and verified.

This review details the threat model, mitigation controls, and verification evidence for Phase F9.

---

## 2. Threat Modeling & Defensive Mitigations

### 2.1 Threat: Private Key Compromise via Administrative Ingestion
- **Threat Vector:** Attackers or misconfigured workflows attempt to upload or enter private keys into the trust registry.
- **Architectural Defense:** **Zero Private Key Policy**.
  - The frontend registration modal (`RegisterKeyModal.tsx`) explicitly warns operators that private key material is never accepted.
  - Form validation expects X.509 `-----BEGIN PUBLIC KEY-----` headers and rejects private key markers (`-----BEGIN RSA PRIVATE KEY-----`, `-----BEGIN PRIVATE KEY-----`).
  - The backend trust registry (`KeyRegistryAdminController.java` and `KeyRegistryService`) only accepts and stores public keys and certificates. Private keys reside solely with document owners and issuing authorities.

### 2.2 Threat: Elevation of Privilege / Unauthorized Administration
- **Threat Vector:** Non-admin users (e.g. `ROLE_HOLDER`, `ROLE_VERIFIER`, or unauthenticated actors) attempt to access `/administration` or `/audit-logs`.
- **Architectural Defense:**
  - Route-level defense-in-depth: `App.tsx` guards `/administration` and `/audit-logs` using `<RequireRole roles={['ROLE_ADMIN']}>`.
  - Unauthorized users are immediately intercepted with an `AccessDeniedCard` alert and denied rendering of any administrative UI components.
  - Backend enforcement: All `/api/admin/**` endpoints are protected by Spring Security (`@PreAuthorize("hasRole('ADMIN')")`). Even if a client bypasses the frontend guard, the API returns `403 Forbidden` with zero data leakage.

### 2.3 Threat: Accidental or Malicious Re-Activation of Compromised Keys
- **Threat Vector:** An administrator or attacker attempts to transition a revoked public key back to `ACTIVE`, enabling forged credentials signed by a compromised key to be treated as valid.
- **Architectural Defense:** **Terminal Compromise Transition Guard**.
  - The state transition modal (`KeyStatusModal.tsx`) enforces strict status transition rules: once a key is in the `REVOKED` state, it cannot be transitioned to any other state.
  - Selecting `REVOKED` triggers a persistent, high-visibility danger alert explaining that revocation signals compromise and is permanent and irreversible.
  - Backend validation strictly rejects transitions out of `REVOKED`.

### 2.4 Threat: Audit Log Tampering or Silent Evidence Suppression
- **Threat Vector:** Malicious actors attempt to tamper with security audit records, truncate logs, or modify event outcomes.
- **Architectural Defense:** **Continuous SHA-256 Hash Chain Verification**.
  - Each audit event contains a sequence number, previous entry hash `H(n-1)`, and current entry hash `H(n) = SHA-256(H(n-1) || sequence || timestamp || category || type || actor || action || target || outcome || metadata)`.
  - The `AuditIntegrityCard.tsx` interface allows running cryptographic hash-chain validation across sequence boundaries via `/api/admin/audit/verify-integrity`.
  - Any insertion, deletion, or alteration of audit events breaks the mathematical hash chain and is immediately reported with the exact sequence number where the breach occurred (`firstInvalidSequence`).

### 2.5 Threat: Cross-Site Scripting (XSS) via Audit Event Payloads
- **Threat Vector:** Attackers inject malicious JavaScript into user-agent strings, usernames, actor identifiers, or event metadata hoping to achieve script execution in the admin portal.
- **Architectural Defense:**
  - Strict absence of `dangerouslySetInnerHTML` throughout all admin and audit components.
  - Event metadata is rendered as formatted code blocks or JSON trees with proper character escaping.
  - React's default JSX string escaping protects all tabular cells, sequence tags, and actor labels.

### 2.6 Threat: Memory Exhaustion via Unbounded Audit Queries (DoS)
- **Threat Vector:** Querying tens of thousands of security audit events simultaneously causes frontend DOM freezes or browser tab crashes.
- **Architectural Defense:**
  - Server-side pagination enforced via `page` and `size` query parameters (`size` bounded to 10, 25, 50, or 100).
  - Clean table virtualization and bounded pagination controls prevent memory ballooning.
  - JSON export is performed on the currently loaded bounded page or queried range, generating a controlled `Blob` URL that is cleaned up with `URL.revokeObjectURL`.

### 2.7 Threat: Misrepresentation of Cryptographic Boundaries
- **Threat Vector:** The admin UI simulates zero-knowledge predicate proof verification with mocked outcomes or falsely claims the admin has verifier privileges.
- **Architectural Defense:**
  - In `PredicateInspectionCard.tsx`, the platform maintains strict truthfulness regarding cryptographic and authorization boundaries.
  - The component clearly informs administrators that predicate proofs must be verified by relying parties holding `ROLE_VERIFIER` through the dedicated `/verify` portal, upholding the principle of least privilege and preventing administrative trust conflation.

---

## 3. Compliance & Security Verification Matrix

| Security Control | Implementation Mechanism | Verification Test |
| :--- | :--- | :--- |
| **RBAC Route Guarding** | `RequireRole` (`ROLE_ADMIN`) | `App.test.tsx`, `adminComponents.test.tsx` |
| **Zero Private Key Policy** | Banner + form validation | `RegisterKeyModal.tsx`, `adminComponents.test.tsx` |
| **Revocation Immutability** | Terminal status disablement | `KeyStatusModal.tsx`, `adminComponents.test.tsx` |
| **Hash-Chain Verification** | `/api/admin/audit/verify-integrity` | `AuditIntegrityCard.tsx`, `auditComponents.test.tsx` |
| **Double-Confirmation Modal** | High-impact lifecycle actions | `SystemOverviewCard.tsx`, `CredentialOversightCard.tsx` |
| **XSS Prevention** | React JSX escaping, no innerHTML | Code audit, `auditComponents.test.tsx` |
| **Backend Integration** | Spring Security JWT + Flyway v13 | `FrontendIntegrationTest.java` (PASS) |
