# Phase 2 — Open Product Decisions & Technical Inquiries
## Zero-Knowledge Document Verification Platform

---

## 1. Overview

This document records the open product decisions, architectural uncertainties, and backend boundary inquiries identified during **Phase 2: UX Research & User Journey Planning** for the **Zero-Knowledge Document Verification Platform**.

In accordance with strict technical auditing standards, assumptions have not been silently filled. Instead, each inquiry is documented with the exact repository evidence examined, its UX impact, a recommended default resolution, and an assessment of whether it blocks **Phase 3 (Frontend Architecture & Design System)**.

---

## 2. Inquiries & Decision Register

### Q-UX-001: Anonymous Public Verification History Retention
- **Inquiry:** Should the frontend record recent verification checks in the user's browser `sessionStorage`, or should verification checks remain strictly stateless with zero client-side retention?
- **Why It Matters:** Verifiers frequently verify multiple documents in a single inspection session (e.g., admissions officer checking student transcripts). Having a "Recent Verifications" history drawer improves productivity. However, storing verification references in `sessionStorage` could expose verified document titles to subsequent users on a shared public terminal.
- **Evidence Examined:** [`verifierStore.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/state/verifierStore.ts) maintains an in-memory `verificationHistory: VerificationResult[]` array. The backend does not provide an endpoint to query past anonymous public verifications.
- **Recommended Default:** Maintain recent verifications in `sessionStorage` capped at the 5 most recent records, with a prominent `"Clear History"` button and an automatic purge when the browser tab is closed.
- **Decision Owner:** Product Owner / Privacy Officer.
- **Blocks Phase 3?:** **No.** Phase 3 design tokens and component styling can proceed; the history drawer can be designed with toggleable visibility.

---

### Q-UX-002: Credential Revocation Authority for Holders
- **Inquiry:** Should credential holders be permitted to permanently **revoke** their own credentials, or should their self-service authority be restricted strictly to **suspension** (with permanent revocation reserved exclusively for the issuing organization)?
- **Why It Matters:** Permanent revocation is an irreversible cryptographic state. In legal, educational, and medical contexts, holders generally cannot invalidate an issued diploma or license unilaterally; they can only suspend public verification of their credential copy.
- **Evidence Examined:** [`HolderCredentialController.java`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-credential/src/main/java/com/zkdoc/credential/JdbcHolderCredentialStore.java) and [`endpoints.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/api/endpoints.ts) expose both `HOLDER_CREDENTIAL_SUSPEND` (`/api/holder/credentials/{id}/suspend`) and `HOLDER_CREDENTIAL_REVOKE` (`/api/holder/credentials/{id}/revoke`).
- **Recommended Default:** In the Phase 7 holder UI, restrict self-service actions to **Suspend** and **Reactivate**, requiring users who wish to permanently revoke to contact the issuing authority, unless explicit admin override is present.
- **Decision Owner:** Application Security Architect / Compliance Officer.
- **Blocks Phase 3?:** **No.** Status badges and dialog primitives in Phase 3/4 support both actions.

---

### Q-UX-003: Document Upload Retention & Download Policy
- **Inquiry:** Can an institutional issuer or administrator download the raw original document file after it has been registered, or does the platform treat the server-side document storage as write-only/verification-only?
- **Why It Matters:** If users expect the platform to function as an archive/document retrieval system, the UI must provide `"Download Original Document"` actions. If the platform is strictly a cryptographic verification notary, providing document download adds unnecessary data exfiltration surface.
- **Evidence Examined:** [`DocumentController.java`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-api/src/main/java/com/zkdoc/api/document/DocumentController.java) provides `GET /api/documents/{id}/qr` and verification endpoints, but does **not** expose a `GET /api/documents/{id}/download` endpoint for raw binary retrieval. Artifacts are stored in `JdbcArtifactStore`.
- **Recommended Default:** Clearly state in the upload workflow that the platform is a **Cryptographic Verification Notary**, not a cloud storage drive. The UI will provide downloads for verification QR codes and cryptographic receipts, but will not promise raw document re-downloads.
- **Decision Owner:** Backend Architect / Product Owner.
- **Blocks Phase 3?:** **No.** Eliminates unneeded download button variants from Phase 3/4 components.

---

### Q-UX-004: Bulk Credential Issuance Support
- **Inquiry:** Does the institutional issuer workflow require bulk issuance (e.g., uploading a CSV or batch JSON file to issue 500 diplomas simultaneously), or is single-credential wizard issuance sufficient for the target release?
- **Why It Matters:** A multi-step wizard is ideal for single, high-stakes credential issuance, while enterprise registrars often demand batch processing with validation tables and asynchronous job status tracking.
- **Evidence Examined:** Backend endpoint `POST /api/credentials/issue` in `CredentialController.java` accepts a single `IssueCredentialRequest` payload with claims map. There is currently no batch issuance endpoint.
- **Recommended Default:** Standardize Phase 6 on the single-credential guided wizard pattern. Design the architecture modularly so a bulk CSV upload tab can be incorporated in future releases without modifying the core issuance wizard.
- **Decision Owner:** Product Manager.
- **Blocks Phase 3?:** **No.** The wizard layout and form primitives in Phase 3/4 support both.

---

### Q-UX-005: Zero-Knowledge Range Proof Parameter Configurations
- **Inquiry:** Should the frontend allow arbitrary custom predicate rules for Bulletproofs range proofs (e.g., custom mathematical formulas), or should it restrict users to predefined standard templates (`Age >= 18`, `Age >= 21`, `Income >= Threshold`, `Credit Score >= 700`)?
- **Why It Matters:** Custom predicate builders introduce high cognitive burden and validation complexity for holders, whereas template-driven selectors make zero-knowledge proofs accessible to non-technical users.
- **Evidence Examined:** [`CredentialPredicateController.java`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-credential/src/main/java/com/zkdoc/credential/JdbcCredentialStore.java) verifies predicates based on standard comparison operators (`>=`, `<=`, `==`, `in_range`) against numerical commitments.
- **Recommended Default:** Provide a curated set of standard pre-configured templates for common verification claims, with an "Advanced Custom Rule" accordion for power users.
- **Decision Owner:** Cryptography Lead / UX Designer.
- **Blocks Phase 3?:** **No.**

---

### Q-UX-006: Visual Brand Identity: Dark-First vs Dual-Mode Neutral
- **Inquiry:** Should the frontend redesign follow a specialized **Dark-First Cybersecurity Aesthetic** (slate-950, deep navy, emerald-400 verification glow, cyan accents) or a **Dual-Mode Neutral Enterprise Aesthetic** (light mode clean slate-50/white default with optional dark mode toggle)?
- **Why It Matters:** Aesthetic direction dictates design token definitions in Phase 3 (`frontend/src/styles/tokens.css`), contrast ratios for WCAG 2.1 AA compliance, and typography weights.
- **Evidence Examined:** Existing [`tokens.css`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/styles/tokens.css) and components currently use dark slate classes (`bg-slate-900`, `text-slate-100`, `border-slate-800`).
- **Recommended Default:** Implement a **Modern Dual-Mode Enterprise Architecture** that defaults to a sleek, high-contrast dark theme (4.8:1+ contrast) while structuring all color tokens as semantic CSS variables (`--bg-primary`, `--text-primary`, `--border-subtle`), enabling full light-mode switching without component-level refactoring.
- **Decision Owner:** Project Stakeholder / Lead Designer.
- **Blocks Phase 3?:** **Yes, for final token palette.** Requires confirmation prior to finalizing token values in Phase 3.

---

## 3. Summary & Escalation Matrix

| Question ID | Domain | Urgency | Blocks Phase 3? | Recommendation Status |
| :--- | :--- | :---: | :---: | :--- |
| **Q-UX-001** | Privacy / Storage | Low | No | Ready: Default to 5-item `sessionStorage` with clear button. |
| **Q-UX-002** | Holder Permissions | Medium | No | Ready: Restrict self-service to Suspend/Reactivate. |
| **Q-UX-003** | Document Retention | Medium | No | Ready: Disclose Verification Notary role (no file download). |
| **Q-UX-004** | Issuer Capabilities | Low | No | Ready: Single-credential wizard for Phase 6. |
| **Q-UX-005** | Cryptographic UX | Low | No | Ready: Curated predicate templates with advanced drawer. |
| **Q-UX-006** | Visual Direction | **High** | **Yes (Tokens)**| Ready: Dual-mode semantic tokens defaulting to Dark Slate. |
