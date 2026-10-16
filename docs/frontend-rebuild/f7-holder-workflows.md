# F7: Holder Workflows — Secure Credential Access, Credential Management, and Privacy-Preserving Holder Experience

**Project:** Zero-Knowledge Document Verification Platform  
**Phase:** Frontend Phase F7  
**Document ID:** ZKDOC-FE-F7-001  
**Status:** Approved Implementation Report  
**Date:** October 2026  

---

## 1. Executive Summary & Objectives

Frontend Phase F7 implements the production-grade **Holder Workflows** for the Zero-Knowledge Document Verification Platform, establishing an authoritative, privacy-preserving credential wallet, secure credential provisioning, lifecycle management, and Zero-Knowledge Proof (ZKP) generation.

In strict alignment with the platform's backend implementation (Spring Boot, PostgreSQL, Spring Security OAuth2 Resource Server, Bulletproof cryptographic range proofs, and verification capability minting):
1. **Zero Mock Endpoints or Fabricated Credentials:** Every holder operation interacts directly with verified backend controllers (`HolderController.java`, `CredentialController.java`, and `VerificationCapabilityController.java`). No synthetic credentials, simulated backend responses, or fake wallet data exist.
2. **Privacy Enclave & Minimal Disclosure:** In compliance with the platform's zero-knowledge architecture, the frontend operates strictly on public metadata, cryptographic commitments, and ZKP proof artifacts. Raw attribute secrets, blinding factors, and commitment openings are never exposed to, cached by, or handled in frontend state.
3. **Bulletproof Zero-Knowledge Range Proofs:** Holders can generate zero-knowledge range proofs (`>= threshold`) over committed credential attributes, utilizing cryptographically secure 16-byte random nonces generated via `crypto.getRandomValues()` to eliminate proof-replay attacks.
4. **Time-Bounded Verification Capabilities & QR Tokens:** Rather than downloading raw documents or exposing private credentials, holders mint time-bounded, scoped verification capabilities and export them as cryptographically verifiable QR codes.
5. **Audited Holder Lifecycle Operations:** Holders can self-manage their credential states (`SUSPEND`, `ACTIVATE`, `REVOKE`) with mandatory audit justification reasons, backed by chronological state transition history inspection.
6. **Reactive State Management & Ephemeral Proof Cache:** State is managed via `useSyncExternalStore` in `holderStore.ts`, incorporating status filters, keyword search, and an in-memory proof artifact cache capped at 20 entries to prevent client memory bloat.

---

## 2. Backend Contract Alignment & Ground Truth

All frontend holder workflows strictly interface with the authoritative backend REST endpoints:

| Functional Area | HTTP Method & Path | Controller / Service | Security & Auth Constraints |
| :--- | :--- | :--- | :--- |
| **List Holder Credentials** | `GET /api/holder/credentials` | `HolderController.java` | Authenticated Holder (`ROLE_HOLDER` or `ROLE_ADMIN`) |
| **Get Credential Detail** | `GET /api/holder/credentials/{id}` | `HolderController.java` | Authenticated Holder (`ROLE_HOLDER` or `ROLE_ADMIN`) |
| **Provision Credential** | `POST /api/holder/credentials/provision` | `HolderController.java` | Authenticated Holder; Request body: `{ credentialId }` |
| **Self-Suspend Credential** | `POST /api/holder/credentials/{id}/suspend` | `HolderController.java` | Authenticated Holder; Request body: `{ reason }` |
| **Reactivate Credential** | `POST /api/holder/credentials/{id}/activate` | `HolderController.java` | Authenticated Holder; Request body: `{ reason }` |
| **Revoke Credential** | `POST /api/holder/credentials/{id}/revoke` | `HolderController.java` | Authenticated Holder; Terminal state; Request body: `{ reason }` |
| **Generate ZKP Proof** | `POST /api/holder/credentials/proofs/generate` | `HolderController.java` | Bulletproof range proof; Request body: `{ credentialId, attributeName, threshold, predicate, nonce }` |
| **Verify Credential** | `GET /api/credentials/{id}/verify` | `CredentialController.java` | Validates digital signature against issuer key authority |
| **Lifecycle History** | `GET /api/credentials/{id}/lifecycle/history` | `CredentialController.java` | Chronological audit trail of state transitions |
| **Mint Capability** | `POST /api/verification/capabilities` | `VerificationCapabilityController.java` | Generates time-bounded, single- or multi-use verification token |
| **Capability QR Code** | `GET /api/verification/capabilities/{id}/qr` | `VerificationCapabilityController.java` | Produces `image/png` binary QR code stream |

---

## 3. Architecture & Modular Structure

The holder feature is encapsulated within `frontend/src/features/holder/` and routed via `frontend/src/routes/HolderDashboardPage.tsx`:

```
frontend/src/
├── api/
│   ├── client.ts                                 # Fetch wrapper supporting JSON and Blob streams
│   ├── endpoints.ts                              # Centralized holder & capability endpoints
│   ├── holderApi.ts                              # Typed client for all 11 backend holder endpoints
│   └── types.ts                                  # Authoritative TypeScript interfaces for holder domain
├── features/holder/
│   ├── components/
│   │   ├── HolderCredentialsListCard.tsx         # Wallet table, status summary banner, search & filters
│   │   ├── ProvisionCredentialCard.tsx           # Credential provisioning form with UUID validation
│   │   ├── HolderStatusActionModal.tsx           # Audited state transition modal (suspend/activate/revoke)
│   │   ├── HolderCredentialDetailModal.tsx       # Keyed modal with signature verification & audit history
│   │   ├── PredicateProofGeneratorCard.tsx       # Bulletproof ZKP range proof generator & artifact display
│   │   └── HolderCapabilityQrCard.tsx            # Verification capability minter & QR blob renderer
│   └── index.ts                                  # Feature barrel exports
├── routes/
│   └── HolderDashboardPage.tsx                   # Tabbed holder workspace (wallet, provision, zkp, capability)
├── state/
│   └── holderStore.ts                            # Reactive holder store with 20-entry proof cache
└── app/
    └── App.tsx                                   # Route registration (/credentials & /holder)
```

---

## 4. Detailed Component & Workflow Specifications

### 4.1 Holder Credentials Wallet (`HolderCredentialsListCard.tsx`)
- **Metric Banners:** Computes real-time counts across loaded credentials: Total, Active, Suspended, and Revoked.
- **Search & Filter Controls:** Uses `SearchFilterBar` with text query matching (by credential type, ID, or issuer DID) and status pills (`ALL`, `ACTIVE`, `SUSPENDED`, `REVOKED`, `EXPIRED`).
- **Interactive Action Triggers:** Each row allows:
  - Viewing full cryptographic detail and signature status.
  - Generating zero-knowledge predicate proofs.
  - Minting scoped verification capabilities.
  - Transitioning lifecycle states (Suspend, Reactivate, Revoke).
- **Graceful States:** Displays `Skeleton` rows during loading, `ErrorState` with retry on network failure, and contextual `EmptyState` when no credentials match the filter.

### 4.2 Credential Provisioning Workflow (`ProvisionCredentialCard.tsx`)
- **Strict UUID Format Validation:** Validates standard UUID syntax (`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`) before dispatch.
- **Direct Wallet Addition:** Calls `POST /api/holder/credentials/provision`, receives authoritative `HolderCredentialResponse`, and automatically updates `holderStore` without requiring a full reload.
- **Immediate Navigation:** Provides an instant "Inspect Credential" action button once provisioning succeeds.

### 4.3 Audited Lifecycle Management Modal (`HolderStatusActionModal.tsx`)
- **Action Semantics:** Supports `suspend`, `activate`, and `revoke` transitions with tailored headers and confirm button variants (`warning` for suspend, `primary` for activate, `danger` for revoke).
- **Mandatory Justification Reason:** Enforces minimum 3-character audit rationale before allowing submission.
- **Immediate State Synchronization:** Upon success, updates the credential record in `holderStore` and triggers a success toast notification.

### 4.4 Metadata Inspection & Audit Timeline (`HolderCredentialDetailModal.tsx`)
- **React 19 Rule-of-Hooks Architecture:** Deconstructs the modal into a container dialog and a keyed inner body component (`<HolderCredentialDetailBody key={credential.id} ... />`), ensuring local verification state and lifecycle timelines reset cleanly between credential selections without causing `react-hooks/set-state-in-effect` errors.
- **Digital Signature Verification:** Invokes `GET /api/credentials/{id}/verify` to validate the RSA-PSS signature against the issuer key authority, rendering a `VerificationBadge` (`VALID` or `INVALID`).
- **Cryptographic Fingerprint Display:** Shows authoritative digest using `<FormattedHash>` with copy-to-clipboard functionality.
- **Audit Transition Timeline:** Queries `GET /api/credentials/{id}/lifecycle/history` and renders chronological state transitions with actor principals, reasons, and timestamps.

### 4.5 Bulletproof Zero-Knowledge Range Proof Generator (`PredicateProofGeneratorCard.tsx`)
- **Attribute & Predicate Selection:** Configures the target attribute name and threshold value with `>=` (Greater-Than-or-Equal) range predicate.
- **Cryptographic Anti-Replay Nonce:** Includes a 16-byte fresh nonce generator using the browser's cryptographically secure pseudo-random number generator (`crypto.getRandomValues(new Uint8Array(16))`), formatted as hex.
- **Proof Execution:** Dispatches `POST /api/holder/credentials/proofs/generate` and displays the authoritative response:
  - Proof format (`BULLETPROOF_V1`).
  - Verification state (`VERIFIED`).
  - Execution duration in milliseconds.
  - Authoritative presentation token.
  - Raw JSON presentation payload with copy-to-clipboard utility.
- **Cache Persistence:** Automatically adds the generated artifact to `holderStore.proofCache` for quick inspection during the active session.

### 4.6 Verification Capability Minting & QR Display (`HolderCapabilityQrCard.tsx`)
- **Configurable Access Constraints:** Allows holders to configure token validity (minutes) and allowed usage count (single-use or multi-use).
- **Binary QR Code Stream:** Fetches `image/png` from `GET /api/verification/capabilities/{id}/qr` via `fetchBlob()`, creating an ephemeral `blob:` URL for the `<img>` tag.
- **Memory Safety & Leak Prevention:** Employs `URL.revokeObjectURL()` inside cleanup functions and state setters, ensuring binary blobs are immediately garbage collected when unmounted or refreshed.

---

## 5. Privacy Enclave & Minimal Disclosure Architecture

The platform's core zero-knowledge principles are strictly enforced throughout Phase F7:

```
┌─────────────────────────────────────────────────────────────┐
│                 HOLDER BROWSER WORKSPACE                    │
│                                                             │
│  [ Public Metadata & UUIDs ]  ──► Displayed in Wallet UI    │
│  [ Cryptographic Commitments] ──► Rendered as FormattedHash │
│  [ Bulletproof Proof Tokens ] ──► Presentation Payloads     │
│  [ Capability Tokens & QRs ]  ──► Scoped Verification       │
│                                                             │
│  ═════════════════════════════════════════════════════════  │
│  PRIVACY ENCLAVE BOUNDARY (NEVER ENTER CLIENT STATE)        │
│  ─────────────────────────────────────────────────────────  │
│  ❌ Raw Attribute Values (e.g. income, exact age, SSN)      │
│  ❌ Pedersen Blinding Factors / Commitment Openings         │
│  ❌ Underlying Plaintext Documents                         │
│  ❌ Raw Document Binary Downloads                           │
└─────────────────────────────────────────────────────────────┘
```

1. **Zero Witness Exposure:** Frontend models only store what the backend emits: `attributeCommitments`, `credentialType`, `status`, `issuerDid`, and cryptographic hashes. Raw attribute values are never retained in browser memory.
2. **Predicate Verification Without Disclosure:** When proving age or income requirements, only the statement (e.g., `age >= 21`) and the resulting Bulletproof presentation token are shared with verifiers.
3. **Capability-Gated Access:** Rather than downloading raw document files, the holder generates time-bounded, usage-constrained capability tokens for verifiers.

---

## 6. State Architecture & Observer Store (`holderStore.ts`)

The holder store utilizes `useSyncExternalStore` for flicker-free, concurrent-mode React 19 integration:

```typescript
export interface HolderStoreState {
  credentials: HolderCredentialResponse[];
  selectedCredential: HolderCredentialResponse | null;
  proofCache: HolderPredicateProofResponse[];
  statusFilter: 'ALL' | 'ACTIVE' | 'SUSPENDED' | 'REVOKED' | 'EXPIRED';
  searchQuery: string;
}
```

- **Proof Cache Bounds:** Capped at 20 entries using FIFO eviction (`slice(0, 20)`) to prevent DOM memory accumulation during long sessions.
- **Optimistic Store Updates:** When status actions (suspend, activate, revoke) succeed, the internal `credentials` array is updated immutably in place without requiring an expensive full-list refetch.
- **Session Isolation:** Does not persist sensitive proofs or credentials to `localStorage`, adhering strictly to ADR-003.
