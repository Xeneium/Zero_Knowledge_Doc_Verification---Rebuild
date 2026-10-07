# Credential Verification Protocol & Diagnostics

**Document Version:** 1.0.0  
**Phase:** Phase 4 — Credential Issuance & RSA-PSS Trust Chain  
**Status:** Approved Security Specification  

---

## 1. Verification Protocol Workflow

Verification is initiated via `GET /api/credentials/{id}/verify`. The system executes the following deterministic checklist:

```text
Check 1: Database Lookup
         ├── Found ──────────────► Continue
         └── Not Found ──────────► Throw CredentialNotFoundException (404)

Check 2: Digest Recomputation
         ├── hasher.hash(canonical_payload) == digest ──► Continue
         └── Mismatch ──────────────────────────────────► Reject (digestMatches=false)

Check 3: Canonical Payload Decoding
         ├── CanonicalCredentialDecoder.decode() ────────► Continue
         └── Decoding Exception ─────────────────────────► Reject (malformed payload)

Check 4: Operational Status & Expiration
         ├── status == ACTIVE and now <= expiresAt ───────► Continue
         └── Inactive or Expired ────────────────────────► Reject (operationalStatusValid=false)

Check 5: Issuer Authority Validation
         ├── IssuerStore.findById(issuerId).status == ACTIVE ──► Continue
         └── Suspended or Revoked ─────────────────────────────► Reject (issuerActive=false)

Check 6: RSASSA-PSS Public Key Signature Verification
         ├── KeyRegistryService.verifyHistorical() == true ────► Verified (verified=true)
         └── Signature Mismatch or Revoked Key ────────────────► Reject (signatureValid=false)
```

---

## 2. Granular Diagnostic Indicators

The response payload returned by `CredentialVerificationResult` provides clear, unambiguous diagnostic fields:

| Field | Type | Description |
|:---|:---|:---|
| `credentialId` | UUID | Identifier of the verified credential |
| `verified` | boolean | Final comprehensive verification verdict (`true` only if all checks pass) |
| `digestMatches` | boolean | Confirms stored canonical bytes match stored SHA-256 digest |
| `signatureValid`| boolean | Confirms RSASSA-PSS signature is mathematically valid |
| `issuerActive` | boolean | Confirms issuer domain record is in `ACTIVE` state |
| `keyTrusted` | boolean | Confirms signing key belongs to issuer and is recognized in `KeyRegistry` |
| `operationalStatusValid` | boolean | Confirms credential is not revoked or expired |
| `failureReason` | String | Human-readable diagnosis explaining why verification failed (or `null` if successful) |
