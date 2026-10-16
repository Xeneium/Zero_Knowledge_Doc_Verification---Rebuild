# Frontend Phase F1: Backend Integration Contract & API Audit

**Project:** Zero-Knowledge Document Verification Platform  
**Document Version:** 1.0.0  
**Phase:** Frontend Phase F1 — Existing Frontend Audit, Backend Compatibility Assessment, and Safe Replacement Plan  
**Date:** 2026-10-14  
**Author:** AI Engineering Assistant (Pair Programming)  
**Status:** Authoritative Backend Contract  

---

## 1. Executive Summary

This specification establishes the authoritative integration contract between the Spring Boot backend (`zkdoc-api`) and the rebuilt frontend. The backend implementation was audited directly from Java source code, domain models, Spring Security configurations, and integration test suites.

The backend acts as a **Stateless OAuth2 Resource Server** validating cryptographically signed RS256/PS256 JWT tokens against trust anchors registered in its internal `KeyRegistryService`. It does not contain an interactive `/api/auth/login` endpoint or an account directory. It exposes strictly validated REST endpoints for document upload, cryptographic credential issuance, lifecycle management, verification capability minting, public QR resolution, and zero-knowledge Bulletproofs range proof verification.

All API interactions must conform strictly to the schemas, HTTP status codes, security boundaries, and data minimization constraints detailed herein.

---

## 2. API Contract Matrix

| Feature | HTTP Method & Path | Authentication | Authorization | Request Body / Parameters | Response Body | Errors | Frontend Consumer | Evidence Location | Status |
|---|---|---|---|---|---|---|---|---|---|
| **System Liveness / Health** | `GET /api/health` | Public (`permitAll`) | None | None | `{"status":"ok"}` | None expected | Header connection badge, Overview dashboard | `HealthController.java:11` | Confirmed |
| **Document Upload** | `POST /api/documents` | Authenticated Bearer JWT | `ROLE_ISSUER` | `multipart/form-data`: `file` (MultipartFile, max 25MB) | `201 Created`: `{"id":"UUID","digest":"hex"}` | `400`: Empty file / validation failure<br>`401`: Missing/invalid token<br>`403`: Lacks `ROLE_ISSUER` | Issuer Document Workspace | `DocumentController.java:49`, `SecurityConfiguration.java:95` | Confirmed |
| **Verify Stored Document Copy** | `GET /api/documents/{id}/verify` | Authenticated Bearer JWT | `ROLE_ISSUER`, `ROLE_HOLDER`, `ROLE_VERIFIER`, `ROLE_ADMIN` | Path variable: `id` (UUID) | `200 OK`: `VerificationResult`<br>`{"verified":bool,"signatureValid":bool,"documentActive":bool}` | `404`: Document not found<br>`401`: Unauthorized<br>`403`: Forbidden | Verifier Document Workspace | `DocumentController.java:79`, `SecurityConfiguration.java:100` | Confirmed |
| **Verify Uploaded Document Copy** | `POST /api/documents/{id}/verify-upload` | Authenticated Bearer JWT | `ROLE_ISSUER`, `ROLE_HOLDER`, `ROLE_VERIFIER`, `ROLE_ADMIN` | Path variable: `id` (UUID)<br>`multipart/form-data`: `file` | `200 OK`: `VerificationResult`<br>`{"verified":bool,"signatureValid":bool,"documentActive":bool}` | `400`: Empty file<br>`404`: Document not found | Verifier Document Workspace | `DocumentController.java:91`, `SecurityConfiguration.java:100` | Confirmed |
| **Get Document QR Code (Legacy Mode A)** | `GET /api/documents/{id}/qr` | Authenticated Bearer JWT | `ROLE_ISSUER`, `ROLE_ADMIN` (Owner or Admin) | Path variable: `id` (UUID) | `200 OK`: `image/png` (Direct URL QR encoding) | `403`: Not document owner<br>`404`: Document not found | Issuer Document Workspace | `DocumentController.java:109`, `ObjectAuthorizationService.java:85` | Confirmed |
| **Issue Credential** | `POST /api/credentials`<br>`POST /api/credentials/issue` | Authenticated Bearer JWT | `ROLE_ISSUER` (caller must match `issuerId`) | JSON: `IssueCredentialRequest`<br>`{"issuerId":"str","subjectId":"str","attributes":[{"name":"str","value":num}],"validFrom":"iso","validTo":"iso"}` | `201 Created`: `{"credentialId":"UUID","digest":"hex"}` | `400`: Validation failure<br>`403`: Caller is not requested issuer<br>`409`: Conflict | Issuer Credential Issuance Workspace | `CredentialController.java:71`, `ObjectAuthorizationService.java:110` | Confirmed |
| **Authoritative Credential Verify** | `GET /api/credentials/{id}/verify` | Authenticated Bearer JWT | `ROLE_ISSUER`, `ROLE_HOLDER`, `ROLE_VERIFIER`, `ROLE_ADMIN` | Path variable: `id` (UUID)<br>Query param (optional): `asOf` (ISO-8601 Instant) | `200 OK`: `CredentialVerificationResult`<br>`{"credentialId":"UUID","verified":bool,"signatureValid":bool,"operationalStatusValid":bool,"status":"str","asOf":"iso"}` | `400`: Malformed date/UUID<br>`404`: Credential not found | Verifier Credential Workspace | `CredentialController.java:89`, `SecurityConfiguration.java:100` | Confirmed |
| **Get Credential Lifecycle Status** | `GET /api/credentials/{id}/lifecycle` | Authenticated Bearer JWT | `ROLE_ISSUER` (owner), `ROLE_HOLDER` (provisioned), or `ROLE_ADMIN` | Path variable: `id` (UUID) | `200 OK`: `CredentialLifecycleResponse`<br>`{"credentialId":"UUID","status":"ACTIVE|SUSPENDED|REVOKED","version":num,"issuerId":"str","message":"str"}` | `403`: Caller not owner/holder<br>`404`: Credential not found | Credential Detail / Audit Views | `CredentialController.java:176`, `ObjectAuthorizationService.java:173` | Confirmed |
| **Get Credential Lifecycle History** | `GET /api/credentials/{id}/lifecycle/history` | Authenticated Bearer JWT | `ROLE_ISSUER` (owner), `ROLE_HOLDER` (provisioned), or `ROLE_ADMIN` | Path variable: `id` (UUID) | `200 OK`: `List<CredentialLifecycleEvent>`<br>`[{"id":"UUID","credentialId":"UUID","fromStatus":"str","toStatus":"str","actor":"str","role":"str","reason":"str","timestamp":"iso"}]` | `403`: Caller not owner/holder<br>`404`: Credential not found | Credential Detail View | `CredentialController.java:189`, `ObjectAuthorizationService.java:173` | Confirmed |
| **Suspend Credential** | `POST /api/credentials/{id}/suspend` | Authenticated Bearer JWT | `ROLE_ISSUER` (owner) or `ROLE_ADMIN` | Path variable: `id` (UUID)<br>JSON (optional): `{"reason":"str"}` | `200 OK`: `CredentialLifecycleResponse` | `403`: Not owner<br>`409`: Invalid transition / conflict | Issuer Credential Management | `CredentialController.java:110`, `ObjectAuthorizationService.java:133` | Confirmed |
| **Resume Credential** | `POST /api/credentials/{id}/resume` | Authenticated Bearer JWT | `ROLE_ISSUER` (owner) or `ROLE_ADMIN` | Path variable: `id` (UUID)<br>JSON (optional): `{"reason":"str"}` | `200 OK`: `CredentialLifecycleResponse` | `403`: Not owner<br>`409`: Invalid transition / conflict | Issuer Credential Management | `CredentialController.java:132`, `ObjectAuthorizationService.java:133` | Confirmed |
| **Revoke Credential** | `POST /api/credentials/{id}/revoke` | Authenticated Bearer JWT | `ROLE_ISSUER` (owner) or `ROLE_ADMIN` | Path variable: `id` (UUID)<br>JSON (optional): `{"reason":"str"}` | `200 OK`: `CredentialLifecycleResponse` | `403`: Not owner<br>`409`: Invalid transition / conflict | Issuer Credential Management | `CredentialController.java:154`, `ObjectAuthorizationService.java:133` | Confirmed |
| **Provision Holder Credential** | `POST /api/holder/credentials/provision` | Authenticated Bearer JWT | `ROLE_HOLDER`, `ROLE_ADMIN` | JSON: `ProvisionHolderCredentialRequest`<br>`{"credentialId":"UUID"}` | `201 Created`: `HolderCredentialResponse`<br>`{"id":"UUID","holderId":"str","credentialId":"UUID","credentialDigestHex":"hex","status":"ACTIVE","version":num,"provisionedAt":"iso","updatedAt":"iso"}` | `400`: Malformed UUID<br>`404`: Credential not found<br>`409`: Already provisioned | Holder Vault | `HolderCredentialController.java:61`, `HolderCredentialService.java` | Confirmed |
| **List Holder Credentials** | `GET /api/holder/credentials` | Authenticated Bearer JWT | `ROLE_HOLDER`, `ROLE_ADMIN` | None (filtered by caller principal) | `200 OK`: `List<HolderCredentialResponse>` | `401`: Unauthorized | Holder Vault | `HolderCredentialController.java:80` | Confirmed |
| **Get Holder Credential By ID** | `GET /api/holder/credentials/{id}` | Authenticated Bearer JWT | `ROLE_HOLDER` (owner), `ROLE_ADMIN` | Path variable: `id` (UUID) | `200 OK`: `HolderCredentialResponse` | `403`: IDOR protection (not owner)<br>`404`: Not found | Holder Vault Detail | `HolderCredentialController.java:90` | Confirmed |
| **Generate Holder Predicate Proof** | `POST /api/holder/credentials/proofs/generate`<br>`POST /api/holder/credentials/generate-predicate-proof` | Authenticated Bearer JWT | `ROLE_HOLDER` (owner), `ROLE_ADMIN` | JSON: `GeneratePredicateProofRequest`<br>`{"holderCredentialId":"UUID","attributeName":"str","operator":">= (optional)","threshold":num,"nonceHex":"hex (2-128 chars)"}` | `200 OK`: `HolderPredicateProofResponse`<br>`{"holderCredentialId":"UUID","credentialId":"UUID","credentialDigestHex":"hex","attributeName":"str","operator":">= ","threshold":num,"commitmentPointHex":"hex","attestationSignatureHex":"hex","attestationKeyId":"str","nonceHex":"hex","proofVersion":"str","bitSize":num,"proofBytesHex":"hex"}` | `400`: Missing `nonceHex` / bad params<br>`404`: Credential not found<br>`429`: Proof generation capacity saturated | Holder ZKP Proof Modal | `HolderCredentialController.java:130`, `GeneratePredicateProofRequest.java` | Confirmed |
| **Verify ZKP Predicate Proof** | `POST /api/v2/zkp/credential-predicates/verify` | Authenticated Bearer JWT | `ROLE_VERIFIER` | JSON: `PredicateVerificationRequest`<br>`{"proofVersion":"str","credentialId":"UUID","attributeName":"str","threshold":num,"nonce":"base64","proofBytes":"base64"}`<br>*(Strict schema: extraneous fields rejected)* | `200 OK`: `CredentialPredicateVerificationResult`<br>`{"verified":bool}` | `400`: Unsupported field / bad bounds<br>`401`: Unauthorized<br>`403`: Missing `ROLE_VERIFIER`<br>`429`: Capacity saturated | Verifier Predicate Proof Workspace | `CredentialPredicateController.java:53`, `predicate-verification-boundary.md` | Confirmed |
| **Create Verification Capability (QR Minting)** | `POST /api/capabilities` | Authenticated Bearer JWT | `ROLE_ISSUER`, `ROLE_HOLDER`, `ROLE_ADMIN` | JSON: `CreateCapabilityRequest`<br>`{"credentialId":"UUID (opt)","documentId":"UUID (opt)","mode":"DOCUMENT_INTEGRITY|CREDENTIAL_STATUS","ttlSeconds":num}` | `201 Created`: `CapabilityResponse`<br>`{"id":"UUID","capabilityToken":"str (43-char b64)","mode":"str","protocolVersion":"QR_V1","status":"ACTIVE","expiresAt":"iso"}` | `400`: Mode missing / ttl $>30$ days<br>`403`: Caller does not own resource | Issuer / Holder QR Share Dialog | `VerificationCapabilityController.java:58`, `ObjectAuthorizationService.java:228` | Confirmed |
| **Get Capability QR Image** | `GET /api/capabilities/{token}/qr` | Authenticated Bearer JWT | `ROLE_ISSUER`, `ROLE_HOLDER`, `ROLE_ADMIN` | Path variable: `token`<br>Query param (opt): `format=canonical|direct` | `200 OK`: `image/png`<br>Header: `Cache-Control: no-store, no-cache` | `404`: Unknown token<br>`410 GONE`: Expired or revoked capability | Issuer / Holder QR Display | `VerificationCapabilityController.java:98` | Confirmed |
| **Revoke Verification Capability** | `POST /api/capabilities/{id}/revoke` | Authenticated Bearer JWT | Creator or `ROLE_ADMIN` | Path variable: `id` (UUID)<br>Query param (opt): `reason` | `204 No Content` | `403`: Not creator<br>`404`: Capability not found | Issuer / Holder Capability Table | `VerificationCapabilityController.java:145`, `ObjectAuthorizationService.java:278` | Confirmed |
| **Public QR Token Verification (Mode A & B)** | `GET /api/public/verify/{token}` | Public (`permitAll`) | None | Path variable: `token` (String, max 128 chars) | `200 OK`: `PublicVerificationResult`<br>`{"signatureValid":bool,"documentActive":bool}` | `400`: Malformed token<br>`404`: Unknown/expired token<br>`429`: Rate limit exceeded | Public Verification Page | `PublicVerificationController.java:61`, `PublicVerificationRateLimitFilter.java` | Confirmed |
| **Public Capability Verification Details** | `GET /api/public/verify/capability/{token}` | Public (`permitAll`) | None | Path variable: `token` | `200 OK`: `CapabilityPublicVerificationResponse`<br>`{"verified":bool,"valid":bool,"status":"VALID|INVALID","mode":"str","protocolVersion":"str","signatureValid":bool,"active":bool,"failureReason":str}` | `400`: Bad token<br>`404`: Not found | Public Verification Page (Mode B) | `PublicVerificationController.java:128`, `CapabilityPublicVerificationResponse.java` | Confirmed |
| **Query Security Audit Events** | `GET /api/admin/audit/events` | Authenticated Bearer JWT | `ROLE_ADMIN` | Query params: `category`, `type`, `actor`, `targetType`, `targetId`, `correlationId`, `from`, `to`, `page`, `size` | `200 OK`: `AuditQueryResult`<br>`{"items":[SecurityAuditEventResponse],"total":num,"page":num,"size":num,"totalPages":num}` | `401`: Unauthorized<br>`403`: Lacks `ROLE_ADMIN` | Admin Audit Workspace | `AuditAdminController.java:29`, `SecurityConfiguration.java:97` | Confirmed |
| **Verify Audit Hash Chain Integrity** | `GET /api/admin/audit/verify-integrity` | Authenticated Bearer JWT | `ROLE_ADMIN` | Query params: `startSeq` (def 1), `endSeq` | `200 OK`: `AuditIntegrityResult`<br>`{"valid":bool,"totalEventsChecked":num,"startSequence":num,"endSequence":num,"firstBrokenSequence":num,"message":"str"}` | `401`: Unauthorized<br>`403`: Lacks `ROLE_ADMIN` | Admin Audit Workspace | `AuditAdminController.java:55` | Confirmed |
| **List Registered Trust Keys** | `GET /api/admin/keys` | Authenticated Bearer JWT | `ROLE_ADMIN` | None | `200 OK`: `List<KeyMetadataResponse>`<br>`[{"id":"UUID","keyId":"str","purpose":"str","status":"ACTIVE|RETIRED|REVOKED","ownerId":"str","ownerType":"str","algorithm":"str","fingerprint":"str","publicKeyPem":"str","createdAt":"iso",...}]` | `401`: Unauthorized<br>`403`: Lacks `ROLE_ADMIN` | Admin Trust Registry | `KeyRegistryAdminController.java:79` | Confirmed |
| **Get Key Metadata** | `GET /api/admin/keys/{keyId}` | Authenticated Bearer JWT | `ROLE_ADMIN` | Path variable: `keyId` | `200 OK`: `KeyMetadataResponse` | `404`: Key not found | Admin Trust Registry | `KeyRegistryAdminController.java:101` | Confirmed |
| **Register Trust Key** | `POST /api/admin/keys`<br>`POST /api/admin/keys/register` | Authenticated Bearer JWT | `ROLE_ADMIN` | JSON: `KeyRegistrationRequest`<br>`{"keyId":"str","purpose":"DOCUMENT_SIGNING|CREDENTIAL_SIGNING|COMMITMENT_ATTESTATION|JWT_SIGNING","publicKeyPem":"str","status":"ACTIVE","algorithm":"RSA-PSS","ownerId":"str","ownerType":"PLATFORM|ISSUER"}` | `201 Created`: `{"id":"UUID","keyId":"str"}` | `400`: Malformed PEM / invalid algorithm | Admin Trust Registry | `KeyRegistryAdminController.java:42` | Confirmed |
| **Update Key Status** | `PATCH /api/admin/keys/{keyId}/status` | Authenticated Bearer JWT | `ROLE_ADMIN` | Path variable: `keyId`<br>JSON: `{"status":"RETIRED|REVOKED","reason":"str","replacementKeyId":"str"}` | `204 No Content` | `400`: Invalid status transition | Admin Trust Registry | `KeyRegistryAdminController.java:122` | Confirmed |
| **Rotate Key** | `POST /api/admin/keys/{keyId}/rotate` | Authenticated Bearer JWT | `ROLE_ADMIN` | Path variable: `keyId`<br>JSON: `{"replacementKeyId":"str"}` | `204 No Content` | `400`: Unknown replacement key | Admin Trust Registry | `KeyRegistryAdminController.java:130` | Confirmed |
| **Execute Disaster Recovery Reconciliation** | `POST /api/admin/recovery/reconcile` | Authenticated Bearer JWT | `ROLE_ADMIN` | None | `200 OK`: `RecoveryReconciliationReport`<br>`{"status":"FULLY_RECOVERED|DEGRADED|FAILED","fullyRecovered":bool,"totalArtifactsChecked":num,"totalKeysChecked":num,"totalCredentialsChecked":num,"errors":["str"]}` | `401`: Unauthorized<br>`403`: Lacks `ROLE_ADMIN` | Admin Recovery Console | `RecoveryAdminController.java:45` | Confirmed |
| **Get Recovery Status** | `GET /api/admin/recovery/status` | Authenticated Bearer JWT | `ROLE_ADMIN` | None | `200 OK`: `RecoveryReconciliationReport` | `401`: Unauthorized<br>`403`: Lacks `ROLE_ADMIN` | Admin Recovery Console | `RecoveryAdminController.java:68` | Confirmed |

---

## 3. Authentication and Security Integration

### 3.1 Architecture Overview

The backend security layer is implemented in `SecurityConfiguration.java` using Spring Security 6.x:
1. **Stateless Session Policy:** Configured with `SessionCreationPolicy.STATELESS`. The backend never maintains HTTP session state or issues `JSESSIONID` cookies.
2. **CSRF Protection Disabled:** Configured with `.csrf(csrf -> csrf.disable())`. Because all mutating endpoints strictly require an `Authorization: Bearer <token>` header and do not accept ambient credentials (cookies), CSRF is not applicable.
3. **Strict Content-Security-Policy (CSP):** The backend serves HTTP response headers:
   ```
   Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; object-src 'none'
   X-Frame-Options: DENY
   X-Content-Type-Options: nosniff
   Referrer-Policy: strict-origin-when-cross-origin
   ```
4. **JWT Verification Mechanics (`ManagedJwtDecoderProvider.java`):**
   - **Header Validation:** The JWT must have an asymmetric algorithm allowlisted in configuration (default: `RS256`, `PS256`). Symmetric algorithms (`HS256`, `HS384`, `HS512`) are rejected to prevent algorithm confusion attacks. Header `kid` (Key ID) is mandatory. Embedded keys (`jwk`, `jku`) are rejected.
   - **Key Trust Resolution:** The `kid` is resolved against `KeyRegistryService` with key purpose `KeyPurpose.JWT_SIGNING`. Only public keys registered as `ACTIVE` or valid for historical verification are accepted.
   - **Claims Validation:**
     - `iss` must match `zkdoc.security.jwt.issuer` (default: `https://auth.zkdoc.internal`).
     - `aud` must include `zkdoc.security.jwt.audience` (default: `zkdoc-api`).
     - `sub` must be non-blank.
     - `exp` must be after `iat`, with a maximum clock-skew tolerance of 60 seconds.
   - **Role Mapping (`JwtAuthoritiesConverter.java`):** Roles are extracted from the `roles` or `role` claim, uppercase-normalized, prefixed with `ROLE_`, and mapped into Spring Security authorities (`ROLE_ISSUER`, `ROLE_HOLDER`, `ROLE_VERIFIER`, `ROLE_ADMIN`).

### 3.2 Authentication Reality: No In-Band Login Endpoint

The backend **does not implement user credential storage, password verification, or a JWT generation endpoint**. It assumes an external Identity Provider (or enterprise OAuth2 Authorization Server) issues signed JWTs whose public signing keys are registered into the backend's `KeyRegistryService`.

**Implication for Frontend:** The frontend cannot provide a traditional username/password login form against the backend unless an external IdP or development token broker is deployed. For development and testing environments, the frontend must provide a secure token input dialog where authenticated users supply valid, pre-signed Bearer JWTs, or load pre-generated test tokens signed with a registered test key.

### 3.3 Object-Level Authorization (IDOR / BOLA Prevention)

`ObjectAuthorizationService.java` enforces fine-grained authorization beyond coarse role checks:
- **Documents:** `checkDocumentAccess` allows access only if the caller's `sub` equals `document.ownerId()`, or if the caller possesses `ROLE_ADMIN`.
- **Credential Issuance:** `checkCredentialIssuance` ensures an issuer can only issue credentials where `request.issuerId` matches the authenticated `sub`, preventing impersonation.
- **Credential Lifecycle:** `checkCredentialLifecycle` permits mutations (`suspend`, `resume`, `revoke`) only to the issuer who originally created the credential, or an administrator.
- **Credential Lifecycle Inspection:** `checkCredentialLifecycleAccess` permits inspection only to the issuing authority, an administrator, or a `HOLDER` who has been provisioned the credential.
- **Verification Capabilities:** Only the creator or administrator can revoke a capability; holders can only mint capabilities for credentials provisioned to them.

---

## 4. Error Response Envelope Semantics

The backend standardizes all API errors through `GlobalExceptionHandler.java` and `ApiErrorResponse.java`:

```json
{
  "timestamp": "2026-10-14T16:15:30.123456Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/credentials",
  "correlationId": "c8f2a10b-4e12-4389-9e8a-21f4bc123456",
  "validationErrors": [
    {
      "field": "issuerId",
      "message": "issuerId must not be blank"
    }
  ]
}
```

### Frontend Parsing Requirements
1. **Never look for `payload.code`:** The backend error envelope does not include a `code` property. Error classifications must be derived from `status` and `error`.
2. **Display `validationErrors`:** When present, the frontend must render field-specific error messages beneath corresponding form inputs.
3. **Capture and display `correlationId`:** For unexpected errors (500), display the `correlationId` to the user as a support reference.

---

## 5. Zero-Knowledge Cryptographic Proving & Verifying Flow

### 5.1 Sensitive Data Types & Custody Boundaries

| Data Type | Description | Handled by Frontend? | Storage Rule | Risk Mitigation |
|---|---|---|---|---|
| **Raw Documents** | Binary file bytes (PDF, images) | Yes, during upload/verification | In-memory only; cleared after upload | Never cached or written to local storage |
| **Document Hash** | SHA-256 digest hex | Yes, displayed in result | Ephemeral in component state | Public integrity anchor |
| **Signed Credential** | Canonical JSON with RSA-PSS signature | Yes, metadata only | Ephemeral in component state | Signature validated on server |
| **Attribute Commitments** | Curve25519 Pedersen points $C = vG + rH$ | Yes, hex string point only | Ephemeral in component state | Does not reveal plaintext value $v$ |
| **Blinding Factors ($r$)** | Curve25519 256-bit scalar | **NEVER** | Forbidden from frontend memory | Held strictly in backend `HolderProvingMaterialStore` |
| **Attribute Values ($v$)** | Plaintext secret values | Never directly exposed in proof | In-memory only during issuance form | Redacted in proof responses |
| **Proof Nonce** | Replay-protection challenge | Yes, hex string | Generated per proof / challenge | Bound to Fiat-Shamir transcript |
| **Bulletproof Proof Bytes** | Zero-knowledge range proof artifact | Yes, Base64/Hex bytes | Transferred from Holder to Verifier | Exposes zero attribute information |

### 5.2 Cryptographic Execution Architecture

1. **Proof Generation:** Executed entirely on the backend by `WeavechainBulletproofPredicateEngine` via `HolderCredentialController`. The browser never loads WebAssembly or native Bulletproof proving libraries, eliminating client CPU strain and eliminating the risk of leaking blinding factors in browser memory.
2. **Strict Verification Policy:** `CredentialPredicateController` enforces an allowlist of exactly six fields (`proofVersion`, `credentialId`, `attributeName`, `threshold`, `nonce`, `proofBytes`). Any client attempt to inject commitment values or blinding factors triggers an immediate HTTP 400 Bad Request.

---

## 6. Unsupported Backend Capabilities

The frontend rebuild must not fabricate or mock the following capabilities:
1. **No User Registration or Password Reset:** User provisioning and credential recovery do not exist in the backend.
2. **No User Profile API:** There is no endpoint to fetch or update email, avatar, or personal details.
3. **No In-Band Key Generation:** Private keys are never accepted or generated over REST. The Trust Registry accepts only public keys in X.509 PEM format.
4. **No Session Termination / Revoke-All:** JWT authentication is stateless; token expiration is governed by the token's `exp` claim.

---
*End of Document 2 (Backend Integration Contract)*
