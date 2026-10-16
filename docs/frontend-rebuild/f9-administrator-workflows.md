# F9: Administrator Workflows — System Administration, Trust Management, Credential Oversight, and Predicate-Proof Operations

**Project:** Zero-Knowledge Document Verification Platform  
**Phase:** Frontend Phase F9  
**Document ID:** ZKDOC-FE-F9-001  
**Status:** Approved Implementation Report  
**Date:** October 2026  

---

## 1. Executive Summary & Mission

Frontend Phase F9 delivers the enterprise-grade **Administrator Workflows** for the Zero-Knowledge Document Verification Platform. It equips authorized platform administrators (`ROLE_ADMIN`) with high-assurance controls for cryptographic trust management, post-recovery system reconciliation, immutable security audit inspection, continuous hash-chain integrity verification, and credential lifecycle governance.

In strict adherence to the project's cryptographic and zero-trust engineering standards:
1. **Zero Mock Records or Fake Cryptographic Operations:** All administrative operations bind directly to authoritative Spring Boot REST controllers (`/api/admin/keys/**`, `/api/admin/recovery/**`, `/api/admin/audit/**`, and `/api/credentials/**`). The interface never fabricates key metadata, simulates recovery states, or mocks cryptographic signature verifications.
2. **Cryptographic Key Registry Trust Anchors:** Full lifecycle administration for RSA public keys (`ACTIVE`, `SUSPENDED`, `REVOKED`, `EXPIRED`) with explicit enforcement of the platform's **Zero Private Key Policy**—private key material is never accepted, transmitted, or stored by the trust registry.
3. **Irreversible Revocation Protections:** Cryptographic revocation transitions to `REVOKED` are guarded by double-confirmation warnings indicating terminal key compromise; once revoked, keys cannot be re-activated.
4. **Post-Recovery Platform Reconciliation:** Comprehensive health verification across Flyway database schemas, physical document SHA-256 digests, active public keys, credential RSASSA-PSS signatures, and security audit hash chains.
5. **Continuous Audit Hash-Chain Integrity:** Administrators can query paginated, multi-filter audit trails and mathematically verify sequence continuity and SHA-256 hash chains across arbitrary sequence boundaries.
6. **Authoritative Credential Governance:** Administrative oversight enabling lookup by UUID, digital signature verification, complete lifecycle history timeline, and privileged lifecycle overrides (`suspend`, `resume`, `revoke`).
7. **Transparent Predicate Verification Boundaries:** Truthful handling of Zero-Knowledge Bulletproof range-proof architecture, with clear architectural disclosures that actual predicate verification is strictly restricted by backend Spring Security to relying verifiers (`ROLE_VERIFIER`).
8. **Reactive External State Store:** Managed by `adminStore.ts` utilizing React 18's `useSyncExternalStore` for predictable, tearing-free reactive subscriptions.

---

## 2. Backend Endpoint Matrix & Access Rules

All administrative capabilities map directly to verified Spring Boot endpoints defined in `SecurityConfiguration.java` and domain admin controllers:

| Functional Area | HTTP Method & Path | Controller / Service | Security & Auth Constraint | Purpose / Payload |
| :--- | :--- | :--- | :--- | :--- |
| **Recovery Status** | `GET /api/admin/recovery/status` | `RecoveryAdminController.java` | `hasRole('ADMIN')` | Retrieves authoritative post-recovery health metrics |
| **Reconcile Platform** | `POST /api/admin/recovery/reconcile` | `RecoveryAdminController.java` | `hasRole('ADMIN')` | Triggers comprehensive system-wide cryptographic reconciliation |
| **List Public Keys** | `GET /api/admin/keys` | `KeyRegistryAdminController.java` | `hasRole('ADMIN')` | Queries registered trust anchor public keys |
| **Get Key Metadata** | `GET /api/admin/keys/{keyId}` | `KeyRegistryAdminController.java` | `hasRole('ADMIN')` | Fetches single key specification and status |
| **Get Key PEM** | `GET /api/admin/keys/{keyId}/pem` | `KeyRegistryAdminController.java` | `hasRole('ADMIN')` | Retrieves X.509 PEM certificate and SHA-256 fingerprint |
| **Register Public Key** | `POST /api/admin/keys` | `KeyRegistryAdminController.java` | `hasRole('ADMIN')` | Enrolls new RSA public key anchor |
| **Change Key Status** | `POST /api/admin/keys/{keyId}/status` | `KeyRegistryAdminController.java` | `hasRole('ADMIN')` | Transitions key status (`ACTIVE`, `SUSPENDED`, `REVOKED`) |
| **Rotate Key** | `POST /api/admin/keys/{keyId}/rotate` | `KeyRegistryAdminController.java` | `hasRole('ADMIN')` | Retires current key and binds active replacement key |
| **Query Audit Trail** | `GET /api/admin/audit/events` | `AuditAdminController.java` | `hasRole('ADMIN')` | Paginated search with category, actor, and date filters |
| **Get Audit Event** | `GET /api/admin/audit/events/{eventId}` | `AuditAdminController.java` | `hasRole('ADMIN')` | Retrieves audit event with previous/current hash chain linkage |
| **Verify Audit Integrity** | `GET /api/admin/audit/verify-integrity` | `AuditAdminController.java` | `hasRole('ADMIN')` | Validates continuous SHA-256 hash chain between sequence bounds |
| **Credential Lifecycle** | `GET /api/credentials/{id}/lifecycle` | `CredentialController.java` | `hasAnyRole('VERIFIER','ISSUER','HOLDER','ADMIN')` | Authoritative credential status and version |
| **Credential History** | `GET /api/credentials/{id}/lifecycle/history` | `CredentialController.java` | `hasRole('ADMIN')` | Complete chronological lifecycle modification timeline |
| **Verify Credential** | `GET /api/credentials/{id}/verify` | `CredentialController.java` | `hasAnyRole('VERIFIER','ISSUER','HOLDER','ADMIN')` | Validates RSASSA-PSS digital signature and issuer validity |
| **Suspend Credential** | `POST /api/credentials/{id}/suspend` | `CredentialController.java` | `hasAnyRole('ISSUER','ADMIN')` | Suspends active credential |
| **Resume Credential** | `POST /api/credentials/{id}/resume` | `CredentialController.java` | `hasAnyRole('ISSUER','ADMIN')` | Reinstates suspended credential to active |
| **Revoke Credential** | `POST /api/credentials/{id}/revoke` | `CredentialController.java` | `hasAnyRole('ISSUER','ADMIN')` | Irreversibly terminates credential validity |
| **Predicate Proof Verify** | `POST /api/v2/zkp/credential-predicates/verify` | `CredentialPredicateProofController.java` | `hasRole('VERIFIER')` | Architectural boundary; verified via verifier portal |

---

## 3. Architecture & Modular Structure

The administrator and audit capabilities are organized into modular, testable features under `frontend/src/features/admin/`, `frontend/src/features/audit/`, `frontend/src/api/adminApi.ts`, and `frontend/src/state/adminStore.ts`:

```
frontend/src/
├── api/
│   ├── adminApi.ts                              # Typed client for all 17 admin & audit endpoints
│   └── types.ts                                 # Complete admin response, request, and enum types
├── state/
│   └── adminStore.ts                            # useSyncExternalStore reactive store for admin state
├── features/admin/
│   ├── components/
│   │   ├── SystemOverviewCard.tsx               # Platform recovery health, artifact counts & reconciliation
│   │   ├── KeyRegistryCard.tsx                  # Public key table with status filtering, PEM view, status & rotation
│   │   ├── RegisterKeyModal.tsx                 # Public key registration with Zero Private Key Policy guard
│   │   ├── KeyStatusModal.tsx                   # Key lifecycle modification with terminal compromise guard
│   │   ├── KeyRotateModal.tsx                   # Public key rotation modal binding replacement keys
│   │   ├── KeyPemModal.tsx                      # X.509 PEM viewer with SHA-256 fingerprint & clipboard copy
│   │   ├── CredentialOversightCard.tsx          # Credential inspection, signature check, timeline & overrides
│   │   ├── PredicateInspectionCard.tsx          # Bulletproof architecture & verifier boundary notice
│   │   └── index.ts                             # Barrel export
│   └── index.ts
├── features/audit/
│   ├── components/
│   │   ├── AuditIntegrityCard.tsx               # SHA-256 hash-chain verification with sequence boundary inputs
│   │   ├── AuditEventsListCard.tsx              # Paginated audit trail with multi-filtering and JSON export
│   │   ├── AuditEventDetailModal.tsx            # Audit event inspector with hash chain linkage & JSON viewer
│   │   └── index.ts                             # Barrel export
│   └── index.ts
└── routes/
    ├── AdminDashboardPage.tsx                   # Tabbed administrator workspace mounted at /administration
    └── AuditLogsPage.tsx                        # Security audit trail and hash chain validation at /audit-logs
```

---

## 4. Key Administrative Workflows & Components

### 4.1 System Overview & Post-Recovery Reconciliation (`SystemOverviewCard.tsx`)
- **Authoritative Recovery Assessment:** Displays post-recovery health metrics fetched from `/api/admin/recovery/status`, including Flyway database schema version validity, physical document artifact store status (total, valid, missing, corrupted), key registry status, and credential digital signature validation.
- **Controlled Reconciliation Action:** Administrators can execute a system-wide reconciliation via `/api/admin/recovery/reconcile`. The action is protected by a modal warning detailing all verification steps (storage audit, key checks, credential signatures, audit hash chain validation) and logging an `ADMIN_SECURITY_OPERATION` audit event.

### 4.2 Cryptographic Key Registry Trust Management
- **Key Listing & Filtering (`KeyRegistryCard.tsx`):** Displays trust anchors with metadata (Key ID, Algorithm, Purpose, Status, Owner, Registered Date). Features live status-badge filtering (`ALL`, `ACTIVE`, `SUSPENDED`, `REVOKED`, `EXPIRED`).
- **Registration (`RegisterKeyModal.tsx`):** Allows registering new public keys with algorithm specification (e.g. `RSA-PSS`), cryptographic purpose (`CREDENTIAL_SIGNING`, `DOCUMENT_SIGNING`, `COMMITMENT_ATTESTATION_SIGNING`, `JWT_SIGNING`), owner identifier, and owner type (`ISSUER`, `VERIFIER`, `SYSTEM`). Prominently features the **Zero Private Key Policy** banner.
- **Lifecycle Transitions (`KeyStatusModal.tsx`):** Enables authorized status changes. Transitioning to `REVOKED` triggers a critical alert warning that revocation indicates key compromise and is permanent and irreversible.
- **Key Rotation (`KeyRotateModal.tsx`):** Safely retires an existing trust anchor and binds a registered replacement key identifier, recording rotation rationale.
- **Certificate Inspection (`KeyPemModal.tsx`):** Displays clean X.509 public key PEM text with computed SHA-256 fingerprint and one-click clipboard copy.

### 4.3 Immutable Security Audit Log Trail & Hash-Chain Integrity
- **Audit Trail Filtering (`AuditEventsListCard.tsx`):** Searchable table supporting pagination (10, 25, 50, 100 per page) and comprehensive filters: Event Category (`AUTHENTICATION`, `DOCUMENT`, `CREDENTIAL`, `KEY_MANAGEMENT`, `SECURITY`, `SYSTEM`), Actor Subject, and Outcome (`SUCCESS`, `FAILURE`, `DENIED`).
- **Audit Export:** Single-click export of current audit page as a formatted JSON report for external compliance audits.
- **Hash-Chain Linkage (`AuditEventDetailModal.tsx`):** Inspects individual audit entries with cryptographic chain linkage (Previous Entry Hash `H(n-1)` and Current Entry Hash `H(n)`), timestamp, IP address, user agent, and formatted JSON event metadata.
- **Cryptographic Hash-Chain Verification (`AuditIntegrityCard.tsx`):** Allows administrators to execute mathematical SHA-256 hash-chain verification between any two sequence numbers. Displays clean visual confirmation (`CHAIN INTEGRITY CONFIRMED` vs `CHAIN INTEGRITY COMPROMISED`) with first-invalid-sequence reporting.

### 4.4 Credential Oversight & Lifecycle Administration (`CredentialOversightCard.tsx`)
- **Authoritative Lookup:** Administrators look up credentials by UUID, simultaneously retrieving lifecycle state, full chronological lifecycle event history, and cryptographic digital signature validity.
- **Automated Signature Check:** Validates RSASSA-PSS digital signature directly on lookup, displaying `SIGNATURE VALID` or `SIGNATURE INVALID`.
- **Lifecycle History Timeline:** Visual timeline of all state transitions showing event type (`ISSUED`, `SUSPENDED`, `RESUMED`, `REVOKED`), timestamp, actor, transition states (`ACTIVE` → `SUSPENDED`), and justification reasons.
- **Privileged Status Overrides:** Protected action buttons with confirmation modals for `Suspend`, `Resume to Active`, and `Revoke`.

### 4.5 Predicate-Proof Educational Inspector & Verification Boundary (`PredicateInspectionCard.tsx`)
- **Bulletproof Architecture Inspector:** Educational component explaining the cryptographic pipeline: Pedersen commitments (`C = v·G + r·H`), attribute blinding factors, logarithmic Bulletproof inner-product arguments, and Fiat-Shamir challenge nonces.
- **Honest Authorization Boundary:** Transparently informs administrators that the Spring Boot backend (`CredentialPredicateProofController.java`) enforces `@PreAuthorize("hasRole('VERIFIER')")`. Rather than fabricating a simulated verification or bypassing backend constraints, it directs users to the dedicated Verifier Portal (`/verify`) with a `ROLE_VERIFIER` session.

---

## 5. Security Guarantees & Non-Negotiable Standards

1. **Role-Based Access Control:** All administrative routes (`/administration`, `/audit-logs`) and components are guarded by `RequireRole` requiring `ROLE_ADMIN`. Unauthorized users receive access denied alerts with zero leakage of administrative controls.
2. **Zero Private Key Exposure:** The frontend never prompts for, accepts, displays, or transmits private cryptographic keys. All trust registry interactions are strictly limited to public key PEMs and public key identifiers.
3. **Defense Against Open Redirects and DOM Injection:** All audit metadata, reasons, and event payloads are sanitized and rendered using structured React components with zero `dangerouslySetInnerHTML`.
4. **State Machine Integrity:** The admin UI disables invalid transitions (e.g. attempting to resume a `REVOKED` key or credential) and enforces modal confirmations before executing high-impact operations.
