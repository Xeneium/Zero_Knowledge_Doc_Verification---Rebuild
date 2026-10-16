# F8: Verifier API Contract and Integration Reference

**Project:** Zero-Knowledge Document Verification Platform  
**Phase:** Frontend Phase F8  
**Document ID:** ZKDOC-FE-F8-002  
**Status:** Approved Reference Specification  
**Date:** October 2026  

---

## 1. Overview & Protocol Guarantees

This document specifies the exact REST API contracts, cryptographic request/response payloads, authentication boundaries, and error codes consumed by the Phase F8 Verifier Workflows.

All contracts are verified against the Spring Boot backend codebase:
- `SecurityConfiguration.java` (Route authorization rules)
- `VerificationCapabilityController.java` (Public capability verification)
- `DocumentController.java` (Document verification tokens and Mode B file upload)
- `CredentialController.java` (Credential digital signature verification and lifecycle queries)
- `CredentialPredicateProofController.java` (Bulletproof zero-knowledge predicate evaluation)

---

## 2. API Endpoint Specifications

### 2.1. Verify Public Capability Token
- **HTTP Method & Path:** `GET /api/public/verify/capability/{token}`
- **Security Requirement:** `permitAll()` (No JWT required)
- **Parameters:**
  - `token` (path, string): 16–128 character opaque capability token.
- **Backend Response (`200 OK`):**
  ```json
  {
    "verified": true,
    "valid": true,
    "status": "VALID",
    "mode": "MODE_B_REGISTRY_CAPABILITY",
    "protocolVersion": "QR_V1",
    "signatureValid": true,
    "active": true,
    "failureReason": null
  }
  ```
- **Error Codes:**
  - `400 Bad Request`: Token format invalid (length < 16 or invalid characters).
  - `404 Not Found`: Capability token unrecognized, expired, or non-existent.
  - `429 Too Many Requests`: Rate limit threshold exceeded.

---

### 2.2. Verify Public Document Token (Mode A)
- **HTTP Method & Path:** `GET /api/public/verify/{token}`
- **Security Requirement:** `permitAll()` (No JWT required)
- **Parameters:**
  - `token` (path, string): Document verification token generated during issuance.
- **Backend Response (`200 OK`):**
  ```json
  {
    "signatureValid": true,
    "documentActive": true
  }
  ```
- **Error Codes:**
  - `400 Bad Request`: Malformed verification token.
  - `404 Not Found`: Document token not registered in registry.

---

### 2.3. Verify Uploaded Document Copy (Mode B Integrity Verification)
- **HTTP Method & Path:** `POST /api/documents/{id}/verify-upload`
- **Security Requirement:** Authenticated session holding `ROLE_VERIFIER`, `ROLE_ISSUER`, `ROLE_HOLDER`, or `ROLE_ADMIN`.
- **Headers:** `Content-Type: multipart/form-data`
- **Path Parameter:**
  - `id` (path, UUID): Target document unique identifier.
- **Form Data:**
  - `file` (MultipartFile): Candidate binary document file (max 25 MB; PDF, PNG, JPEG, TXT).
- **Backend Response (`200 OK`):**
  ```json
  {
    "documentId": "550e8400-e29b-41d4-a716-446655440000",
    "verified": true
  }
  ```
- **Error Codes:**
  - `400 Bad Request`: File missing or empty.
  - `401 Unauthorized`: Missing or expired Bearer token.
  - `403 Forbidden`: User lacks verifier/issuer/holder/admin role.
  - `404 Not Found`: Target document record does not exist.

---

### 2.4. Verify Stored Server Document Copy (Mode A)
- **HTTP Method & Path:** `GET /api/documents/{id}/verify`
- **Security Requirement:** Authenticated session holding `ROLE_VERIFIER`, `ROLE_ISSUER`, `ROLE_HOLDER`, or `ROLE_ADMIN`.
- **Path Parameter:**
  - `id` (path, UUID): Target document unique identifier.
- **Backend Response (`200 OK`):**
  ```json
  {
    "documentId": "550e8400-e29b-41d4-a716-446655440000",
    "verified": true
  }
  ```

---

### 2.5. Verify Credential Signature & Operational Status
- **HTTP Method & Path:** `GET /api/credentials/{id}/verify`
- **Security Requirement:** Authenticated session holding `ROLE_VERIFIER`, `ROLE_ISSUER`, `ROLE_HOLDER`, or `ROLE_ADMIN`.
- **Path Parameter:**
  - `id` (path, UUID): Credential unique identifier.
- **Query Parameter (Optional):**
  - `asOf` (query, ISO-8601 string): Evaluate validity at a specific historical point in time.
- **Backend Response (`200 OK`):**
  ```json
  {
    "credentialId": "550e8400-e29b-41d4-a716-446655440000",
    "verified": true,
    "status": "ACTIVE",
    "asOf": "2026-10-15T09:00:00Z",
    "revokedAt": null,
    "suspendedAt": null,
    "message": "Signature authentic and active"
  }
  ```

---

### 2.6. Query Credential Lifecycle Status
- **HTTP Method & Path:** `GET /api/credentials/{id}/lifecycle`
- **Security Requirement:** Authenticated session holding `ROLE_VERIFIER`, `ROLE_ISSUER`, `ROLE_HOLDER`, or `ROLE_ADMIN`.
- **Path Parameter:**
  - `id` (path, UUID): Credential identifier.
- **Backend Response (`200 OK`):**
  ```json
  {
    "credentialId": "550e8400-e29b-41d4-a716-446655440000",
    "issuerId": "urn:zkdoc:issuer:government",
    "status": "ACTIVE",
    "issuedAt": "2026-10-14T00:00:00Z",
    "expiresAt": "2027-10-14T00:00:00Z",
    "suspendedAt": null,
    "suspensionReason": null,
    "revokedAt": null,
    "revocationReason": null
  }
  ```

---

### 2.7. Verify Zero-Knowledge Predicate Range Proof
- **HTTP Method & Path:** `POST /api/v2/zkp/credential-predicates/verify`
- **Security Requirement:** Authenticated session holding `ROLE_VERIFIER` or `ROLE_ADMIN`.
- **Concurrency Guard:** Protected by an 8-permit Semaphore in `CredentialPredicateProofController.java` to prevent CPU resource exhaustion. When all 8 permits are saturated, returns `HTTP 429 Too Many Requests`.
- **Request Body (`application/json`):**
  ```json
  {
    "proofVersion": "BULLETPROOF_V1",
    "credentialId": "550e8400-e29b-41d4-a716-446655440000",
    "attributeName": "income",
    "threshold": 50000,
    "nonce": "AQIDBAUGBwgJCgsMDQ4PEA==",
    "proofBytes": "qrvM3e7/ABEiM0RVZneImQ=="
  }
  ```
  *(Note: `nonce` and `proofBytes` must be Base64-encoded strings for Java Jackson byte array mapping).*
- **Backend Response (`200 OK`):**
  ```json
  {
    "verified": true
  }
  ```
- **Error Codes:**
  - `400 Bad Request`: Invalid proof encoding or unknown proof version.
  - `401 Unauthorized`: Unauthenticated request.
  - `403 Forbidden`: User lacks `ROLE_VERIFIER` or `ROLE_ADMIN`.
  - `429 Too Many Requests`: Proof evaluation engine concurrency capacity reached (exceeds 8 concurrent tasks).

---

## 3. Data Transformations & Security Handshake

| Presentation Field | Format in QR/UI | Format Sent to API | Transformation Function |
| :--- | :--- | :--- | :--- |
| `nonce` | Hex string (32 hex chars) | Base64 string | `hexToBase64(nonceHex)` in `qrPayload.ts` |
| `proofBytes` | Hex string | Base64 string | `hexToBase64(proofBytesHex)` in `qrPayload.ts` |
| `threshold` | Form number/string | Positive Integer | `parseInt(threshold, 10)` |
| `token` | Canonical JSON `tok` or URL param | Clean string (16–128 chars) | `parseQrPayload(raw).payload.token` |
