# F9: Administrator & Audit REST API Contract Specification

**Project:** Zero-Knowledge Document Verification Platform  
**Phase:** Frontend Phase F9  
**Document ID:** ZKDOC-FE-F9-002  
**Status:** Approved Technical Specification  
**Date:** October 2026  

---

## 1. Overview & Authentication Baseline

This specification defines the exact REST API contracts used by the Frontend Phase F9 Administrator and Audit workflows. All endpoints are hosted by the Spring Boot backend (`zkdoc-api`) and strictly enforced by Spring Security.

### 1.1 Authentication & Authorization Standard
- **Transport Security:** All communication occurs over HTTPS / TLS.
- **Authentication Header:** All administrative requests MUST include a valid JWT Bearer token:
  ```http
  Authorization: Bearer <valid_jwt_token>
  ```
- **RBAC Enforcement:**
  - Administrative endpoints (`/api/admin/**`) require the authority `ROLE_ADMIN`.
  - Credential lifecycle query endpoints require `ROLE_ADMIN`, `ROLE_ISSUER`, `ROLE_HOLDER`, or `ROLE_VERIFIER`.
  - Credential status modification endpoints (`suspend`, `resume`, `revoke`) require `ROLE_ADMIN` or `ROLE_ISSUER`.
  - ZKP Predicate verification (`/api/v2/zkp/credential-predicates/verify`) strictly requires `ROLE_VERIFIER`.

---

## 2. Platform Recovery & System Reconciliation Endpoints

### 2.1 Get Post-Recovery Status
- **Method & Path:** `GET /api/admin/recovery/status`
- **Controller:** `RecoveryAdminController.java`
- **Access Rule:** `@PreAuthorize("hasRole('ADMIN')")`
- **Response Status:** `200 OK`
- **Response Body (`RecoveryReconciliationReport`):**
  ```json
  {
    "recoveryStatus": "HEALTHY",
    "statusMessage": "All cryptographic anchors and records verified.",
    "reconciliationTimestamp": "2026-10-15T10:00:00Z",
    "schemaValid": true,
    "schemaVersion": "13",
    "totalArtifactsChecked": 128,
    "validArtifacts": 128,
    "missingArtifacts": [],
    "corruptedArtifacts": [],
    "totalKeysChecked": 12,
    "activeKeys": 10,
    "invalidKeys": [],
    "totalCredentialsChecked": 45,
    "validCredentials": 45,
    "invalidCredentials": [],
    "auditChainValid": true,
    "auditEventsChecked": 850
  }
  ```

### 2.2 Trigger Platform Reconciliation
- **Method & Path:** `POST /api/admin/recovery/reconcile`
- **Controller:** `RecoveryAdminController.java`
- **Access Rule:** `@PreAuthorize("hasRole('ADMIN')")`
- **Request Body:** None
- **Response Status:** `200 OK`
- **Response Body:** Same as `RecoveryReconciliationReport` (updated following execution).
- **Audit Action:** Logs an authoritative `ADMIN_SECURITY_OPERATION` audit record.

---

## 3. Cryptographic Key Registry Endpoints

### 3.1 List Registered Public Keys
- **Method & Path:** `GET /api/admin/keys`
- **Controller:** `KeyRegistryAdminController.java`
- **Access Rule:** `@PreAuthorize("hasRole('ADMIN')")`
- **Query Parameters:** None
- **Response Status:** `200 OK`
- **Response Body (`List<KeyMetadataResponse>`):**
  ```json
  [
    {
      "id": "key-uuid-1",
      "keyId": "issuer-alpha-2026-pss",
      "algorithm": "RSA-PSS",
      "purpose": "CREDENTIAL_SIGNING",
      "status": "ACTIVE",
      "ownerId": "urn:zkdoc:issuer:gov",
      "ownerType": "ISSUER",
      "fingerprint": "a1b2c3d4e5f6...",
      "createdAt": "2026-01-01T00:00:00Z",
      "expiresAt": "2027-01-01T00:00:00Z",
      "rotatedToKeyId": null
    }
  ]
  ```

### 3.2 Get Key Metadata by Key ID
- **Method & Path:** `GET /api/admin/keys/{keyId}`
- **Controller:** `KeyRegistryAdminController.java`
- **Access Rule:** `@PreAuthorize("hasRole('ADMIN')")`
- **Path Parameter:** `keyId` (string, max 128 chars)
- **Response Status:** `200 OK`
- **Response Body:** `KeyMetadataResponse`

### 3.3 Get Public Key X.509 PEM
- **Method & Path:** `GET /api/admin/keys/{keyId}/pem`
- **Controller:** `KeyRegistryAdminController.java`
- **Access Rule:** `@PreAuthorize("hasRole('ADMIN')")`
- **Response Status:** `200 OK`
- **Response Body (`PublicKeyPemResponse`):**
  ```json
  {
    "keyId": "issuer-alpha-2026-pss",
    "algorithm": "RSA-PSS",
    "publicKeyPem": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...\n-----END PUBLIC KEY-----",
    "fingerprintSha256": "8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4"
  }
  ```

### 3.4 Register New Public Key
- **Method & Path:** `POST /api/admin/keys`
- **Controller:** `KeyRegistryAdminController.java`
- **Access Rule:** `@PreAuthorize("hasRole('ADMIN')")`
- **Request Body (`KeyRegistrationRequest`):**
  ```json
  {
    "keyId": "issuer-alpha-2026-pss",
    "algorithm": "RSA-PSS",
    "publicKeyPem": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----",
    "purpose": "CREDENTIAL_SIGNING",
    "ownerId": "urn:zkdoc:issuer:gov",
    "ownerType": "ISSUER",
    "expiresAt": "2027-12-31T23:59:59Z"
  }
  ```
- **Response Status:** `201 Created`
- **Response Body:** `KeyMetadataResponse`

### 3.5 Change Public Key Status
- **Method & Path:** `POST /api/admin/keys/{keyId}/status`
- **Controller:** `KeyRegistryAdminController.java`
- **Access Rule:** `@PreAuthorize("hasRole('ADMIN')")`
- **Request Body (`KeyStatusChangeRequest`):**
  ```json
  {
    "status": "SUSPENDED",
    "reason": "Routine key rotation preparation"
  }
  ```
- **Response Status:** `200 OK`

### 3.6 Rotate Public Key
- **Method & Path:** `POST /api/admin/keys/{keyId}/rotate`
- **Controller:** `KeyRegistryAdminController.java`
- **Access Rule:** `@PreAuthorize("hasRole('ADMIN')")`
- **Request Body (`KeyRotateRequest`):**
  ```json
  {
    "replacementKeyId": "issuer-alpha-2027-pss",
    "reason": "Scheduled annual cryptographic key rollover"
  }
  ```
- **Response Status:** `200 OK`

---

## 4. Security Audit Log & Hash-Chain Verification Endpoints

### 4.1 Query Security Audit Events
- **Method & Path:** `GET /api/admin/audit/events`
- **Controller:** `AuditAdminController.java`
- **Access Rule:** `@PreAuthorize("hasRole('ADMIN')")`
- **Query Parameters:**
  - `page` (integer, default: 0)
  - `size` (integer, default: 25)
  - `category` (optional, string: `AUTHENTICATION`, `DOCUMENT`, `CREDENTIAL`, `KEY_MANAGEMENT`, `SECURITY`, `SYSTEM`)
  - `actor` (optional, string)
  - `outcome` (optional, string: `SUCCESS`, `FAILURE`, `DENIED`)
  - `from` (optional, ISO8601 string)
  - `to` (optional, ISO8601 string)
- **Response Status:** `200 OK`
- **Response Body (`AuditQueryResult`):**
  ```json
  {
    "items": [
      {
        "eventId": "audit-uuid-1",
        "sequenceNumber": 42,
        "timestamp": "2026-10-15T09:30:00Z",
        "eventCategory": "AUTHENTICATION",
        "eventType": "LOGIN",
        "actorSubject": "admin@zkdoc.local",
        "actorType": "USER",
        "action": "AUTHENTICATE",
        "targetId": "session-1",
        "targetType": "SESSION",
        "outcome": "SUCCESS",
        "previousHash": "prev-hash-41...",
        "currentHash": "entry-hash-42...",
        "ipAddress": "127.0.0.1",
        "userAgent": "Mozilla/5.0 ...",
        "metadata": {
          "authMethod": "PASSWORD_HASH",
          "assignedRoles": ["ROLE_ADMIN"]
        }
      }
    ],
    "page": 0,
    "size": 25,
    "totalItems": 150,
    "totalPages": 6
  }
  ```

### 4.2 Get Audit Event Detail
- **Method & Path:** `GET /api/admin/audit/events/{eventId}`
- **Controller:** `AuditAdminController.java`
- **Access Rule:** `@PreAuthorize("hasRole('ADMIN')")`
- **Path Parameter:** `eventId` (UUID string)
- **Response Status:** `200 OK`
- **Response Body:** `SecurityAuditEventResponse`

### 4.3 Verify Continuous Audit Hash Chain
- **Method & Path:** `GET /api/admin/audit/verify-integrity`
- **Controller:** `AuditAdminController.java`
- **Access Rule:** `@PreAuthorize("hasRole('ADMIN')")`
- **Query Parameters:**
  - `fromSequence` (optional, integer)
  - `toSequence` (optional, integer)
- **Response Status:** `200 OK`
- **Response Body (`AuditIntegrityResult`):**
  ```json
  {
    "valid": true,
    "eventsVerified": 850,
    "startSequence": 1,
    "endSequence": 850,
    "firstInvalidSequence": null,
    "message": "Continuous SHA-256 hash chain verified successfully."
  }
  ```

---

## 5. Credential Oversight Endpoints

### 5.1 Get Credential Lifecycle Status
- **Method & Path:** `GET /api/credentials/{id}/lifecycle`
- **Controller:** `CredentialController.java`
- **Access Rule:** `@PreAuthorize("hasAnyRole('VERIFIER','ISSUER','HOLDER','ADMIN')")`
- **Response Status:** `200 OK`
- **Response Body (`CredentialLifecycleResponse`):**
  ```json
  {
    "credentialId": "550e8400-e29b-41d4-a716-446655440000",
    "issuerId": "urn:zkdoc:issuer:gov",
    "status": "ACTIVE",
    "version": 1,
    "suspensionReason": null,
    "revocationReason": null
  }
  ```

### 5.2 Get Credential Lifecycle History
- **Method & Path:** `GET /api/credentials/{id}/lifecycle/history`
- **Controller:** `CredentialController.java`
- **Access Rule:** `@PreAuthorize("hasRole('ADMIN')")`
- **Response Status:** `200 OK`
- **Response Body (`List<CredentialLifecycleEventResponse>`):**
  ```json
  [
    {
      "id": "event-uuid-1",
      "credentialId": "550e8400-e29b-41d4-a716-446655440000",
      "eventType": "ISSUED",
      "actor": "urn:zkdoc:issuer:gov",
      "actorRole": "ISSUER",
      "fromStatus": "ACTIVE",
      "toStatus": "ACTIVE",
      "reason": "Initial creation and issuance",
      "timestamp": "2026-01-01T12:00:00Z"
    }
  ]
  ```

### 5.3 Verify Credential Digital Signature
- **Method & Path:** `GET /api/credentials/{id}/verify`
- **Controller:** `CredentialController.java`
- **Access Rule:** `@PreAuthorize("hasAnyRole('VERIFIER','ISSUER','HOLDER','ADMIN')")`
- **Response Status:** `200 OK`
- **Response Body (`CredentialVerificationResponse`):**
  ```json
  {
    "credentialId": "550e8400-e29b-41d4-a716-446655440000",
    "verified": true,
    "message": "RSASSA-PSS digital signature verified successfully against active issuer public key."
  }
  ```

### 5.4 Suspend Credential
- **Method & Path:** `POST /api/credentials/{id}/suspend`
- **Controller:** `CredentialController.java`
- **Access Rule:** `@PreAuthorize("hasAnyRole('ISSUER','ADMIN')")`
- **Query Parameter:** `reason` (optional, string)
- **Response Status:** `200 OK`
- **Response Body:** `CredentialLifecycleResponse` (status: `SUSPENDED`)

### 5.5 Resume Credential
- **Method & Path:** `POST /api/credentials/{id}/resume`
- **Controller:** `CredentialController.java`
- **Access Rule:** `@PreAuthorize("hasAnyRole('ISSUER','ADMIN')")`
- **Query Parameter:** `reason` (optional, string)
- **Response Status:** `200 OK`
- **Response Body:** `CredentialLifecycleResponse` (status: `ACTIVE`)

### 5.6 Revoke Credential
- **Method & Path:** `POST /api/credentials/{id}/revoke`
- **Controller:** `CredentialController.java`
- **Access Rule:** `@PreAuthorize("hasAnyRole('ISSUER','ADMIN')")`
- **Query Parameter:** `reason` (optional, string)
- **Response Status:** `200 OK`
- **Response Body:** `CredentialLifecycleResponse` (status: `REVOKED`)
