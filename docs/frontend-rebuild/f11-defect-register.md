# Phase F11: Defect and Compatibility Register

**Project:** Zero-Knowledge Document Verification Platform  
**Component:** Single-Page Application (SPA) Web Frontend (`frontend/`) & Backend API (`zkdoc-api`)  
**Phase:** F11 (Backend Integration, End-to-End Validation, and Cross-Workflow Consistency)  
**Date:** October 2026  
**Status:** All Known Gaps Mitigated; Zero Blockers  

---

## 1. Overview & Classification Scheme

This defect register logs all discrepancies, behavioral nuances, and environmental edge cases identified during the Phase F11 integration and end-to-end testing cycle. Each item is classified by severity, provided with exact reproduction instructions, documented with its frontend client-side mitigation, and assigned a recommended backend follow-up action for future release cycles.

### Severity Definitions:
- **Critical:** Systemic breakdown of core cryptographic verification or data breach vulnerability.
- **High:** Major workflow disruption requiring client-side structural workarounds.
- **Medium:** Routing, deep-linking, or non-standard error envelope nuances with safe fallbacks.
- **Low:** Minor informational or cosmetic discrepancies.

---

## 2. Active Defect & Limitation Entries

### DEF-F11-001: Missing SPA Deep-Link Routes in `SpaForwardController.java`
- **Severity:** Medium
- **Category:** Routing & Deep-Linking
- **Impacted Components:** `SpaForwardController.java`, React Router client routes (`/login`, `/issuer`, `/holder`, `/forbidden`)
- **Description:**
  `SpaForwardController.java` forwards incoming GET requests to `index.html` for client-side routing. Its current `@GetMapping` mapping covers:
  `{"/", "/verify", "/credentials", "/verification", "/predicate-verification", "/audit-logs", "/administration"}`.
  However, the routes `/login`, `/issuer`, `/holder`, and `/forbidden` were added during the frontend rebuild and are not present in the controller's route list.
- **Reproduction Steps:**
  1. Open a new external browser window or incognito session.
  2. Navigate directly to `http://localhost:8080/issuer` or `http://localhost:8080/holder` by typing the URL into the address bar.
  3. Observe that Spring Boot returns an HTTP 404 Not Found error because the path is neither a static asset nor an annotated Spring MVC mapping.
- **Actual Behavior:**
  HTTP 404 Not Found on external direct URL entry.
- **Expected Behavior:**
  Server forwards the request to `index.html`, allowing React Router to render the appropriate page or redirect to `/login` if unauthenticated.
- **Frontend Mitigation:**
  - In-app navigation via React Router links (`<Link>`, `useNavigate`) operates entirely client-side and functions seamlessly across all routes.
  - The default home navigation links direct users into registered paths (`/credentials`, `/administration`, `/verification`).
- **Recommended Backend Follow-up:**
  Update `SpaForwardController.java` to map all client routes:
  ```java
  @GetMapping(value = {
      "/",
      "/login",
      "/issuer",
      "/holder",
      "/verification",
      "/predicate-verification",
      "/audit-logs",
      "/administration",
      "/forbidden",
      "/verify",
      "/credentials"
  })
  public String forward() {
      return "forward:/index.html";
  }
  ```

---

### DEF-F11-002: Strict Property Whitelisting in `CredentialPredicateController.java`
- **Severity:** Medium
- **Category:** Serialization & API Strictness
- **Impacted Components:** `CredentialPredicateController.java`, `POST /api/v2/zkp/credential-predicates/verify`
- **Description:**
  The predicate verification controller utilizes strict Jackson deserialization properties that immediately fail with HTTP 400 Bad Request if any JSON key outside of `{"proofVersion", "credentialId", "attributeName", "threshold", "nonce", "proofBytes"}` is present in the request body.
- **Reproduction Steps:**
  1. Send a POST request to `/api/v2/zkp/credential-predicates/verify` with a valid proof statement, plus an extra diagnostic field (e.g., `{"clientTimestamp": "2026-10-15T12:00:00Z"}`).
  2. Observe HTTP 400 Bad Request response with Jackson deserialization error.
- **Actual Behavior:**
  Strict 400 error rejection on unrecognized JSON fields.
- **Expected Behavior:**
  Backend either processes the recognized cryptographic fields while ignoring unknown metadata, or the client strictly bounds the payload.
- **Frontend Mitigation:**
  [`verifierApi.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/api/verifierApi.ts) explicitly creates a tightly bounded request object containing exclusively the 6 permitted fields:
  ```typescript
  const payload: CredentialPredicateVerificationRequest = {
    proofVersion: request.proofVersion,
    credentialId: request.credentialId,
    attributeName: request.attributeName,
    threshold: request.threshold,
    nonce: request.nonce,
    proofBytes: request.proofBytes,
  };
  ```
- **Recommended Backend Follow-up:**
  Maintain strict whitelisting as a zero-trust defense, or annotate the backend DTO with `@JsonIgnoreProperties(ignoreUnknown = true)` if future protocol extensions require client metadata.

---

### DEF-F11-003: Pre-requisite Wallet Provisioning for Predicate Proofs
- **Severity:** Low
- **Category:** Domain State Synchronization
- **Impacted Components:** `HolderCredentialController.java`, `POST /api/holder/credentials/proofs/generate`
- **Description:**
  A holder cannot generate a zero-knowledge Bulletproof predicate proof statement for an issued credential until that credential has been explicitly provisioned into their wallet via `POST /api/holder/credentials/provision`. If proof generation is attempted on an unprovisioned credential ID, the backend returns HTTP 404 or 400 because the holder-specific Pedersen commitments have not been calculated and stored.
- **Reproduction Steps:**
  1. Issue a credential as an Issuer.
  2. Switch to Holder identity without provisioning the credential.
  3. Attempt to invoke `holderApi.generatePredicateProof()` directly with the raw credential ID.
  4. Backend returns HTTP 404 Credential Not Found for Holder.
- **Actual Behavior:**
  Proof generation fails if credential is not provisioned.
- **Expected Behavior:**
  Proof generation requires prior wallet provisioning.
- **Frontend Mitigation:**
  The Holder UI ([`HolderDashboardPage.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/routes/HolderDashboardPage.tsx)) enforces this state dependency by requiring users to provision credentials via [`ProvisionCredentialCard.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/features/holder/components/ProvisionCredentialCard.tsx) first. The "Generate Proof" tab only operates on credentials that already exist in the holder's wallet.
- **Recommended Backend Follow-up:**
  None needed; this is expected zero-knowledge architecture. The backend error message is clear and accurately reflects holder ownership boundaries.

---

### DEF-F11-004: HTTP 413 Payload Too Large Non-Standard Error Format
- **Severity:** Low
- **Category:** File Upload & Error Envelopes
- **Impacted Components:** `DocumentController.java`, Spring Boot Multipart Config
- **Description:**
  When a file exceeding 25 MB is uploaded, the embedded Tomcat servlet container terminates the connection or returns an unformatted HTTP 413 Payload Too Large response rather than a structured Spring Boot `ApiErrorResponse` JSON object.
- **Reproduction Steps:**
  1. Bypass client validation and send a 30 MB multipart POST to `/api/documents`.
  2. Observe raw HTTP 413 status without JSON body.
- **Actual Behavior:**
  Raw HTTP 413 response without JSON error envelope.
- **Expected Behavior:**
  Structured JSON error envelope with `status: 413` and descriptive message.
- **Frontend Mitigation:**
  1. Client-side size guard in [`fileValidation.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/lib/utils/fileValidation.ts) intercepts files $> 25,000,000$ bytes before network dispatch.
  2. Fallback parser in [`client.ts`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/api/client.ts) catches non-JSON error bodies and constructs a normalized `ApiError` with the HTTP status code.
- **Recommended Backend Follow-up:**
  Add a `@ExceptionHandler(MaxUploadSizeExceededException.class)` in the global controller advice to serialize standard `ApiErrorResponse` JSON.

---

## 3. Register Summary & Resolution Matrix

| Defect ID | Title | Severity | Current Status | Frontend Mitigation Applied | Recommended Backend Action |
|---|---|---|---|---|---|
| `DEF-F11-001` | Missing SPA Deep-Link Routes in `SpaForwardController` | Medium | Mitigated | Client-side routing links through forwarded routes | Add `/login`, `/issuer`, `/holder`, `/forbidden` to `@GetMapping` |
| `DEF-F11-002` | Strict Property Whitelisting in `CredentialPredicateController` | Medium | Resolved | Strict 6-field DTO construction in `verifierApi.ts` | Retain strict whitelist for security |
| `DEF-F11-003` | Pre-requisite Wallet Provisioning for Predicate Proofs | Low | Expected Behavior | Holder workflow guides provisioning before proofing | None (Expected cryptographic behavior) |
| `DEF-F11-004` | HTTP 413 Payload Too Large Non-Standard Error Format | Low | Mitigated | Client-side 25MB bound + raw error normalization | Add `MaxUploadSizeExceededException` controller advice |
