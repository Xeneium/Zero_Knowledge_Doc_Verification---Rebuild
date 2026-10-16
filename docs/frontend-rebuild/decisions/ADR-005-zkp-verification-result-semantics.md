# ADR-005: Zero-Knowledge Proof Verification Result Semantics

## Status

Accepted

## Context

In zero-knowledge proof systems, mathematical validity is binary and unambiguous. The backend's `ZkpController` exposes `POST /api/v2/zkp/credential-predicates/verify`, returning a boolean response:
```json
{
  "verified": true
}
```
If a proof fails or is mathematically forged, the backend returns:
```json
{
  "verified": false
}
```

The legacy frontend contained a critical bug in `PredicateVerificationPage.tsx`:
```tsx
// LEGACY FLAW:
<Badge variant={result.proofValid !== false ? 'success' : 'danger'}>
  {result.proofValid !== false ? 'Mathematically Valid' : 'Invalid Proof'}
</Badge>
```
Because the backend property was named `verified` (not `proofValid`), `result.proofValid` evaluated to `undefined`. In JavaScript, `undefined !== false` evaluates to `true`! As a result, the legacy UI rendered a green "Mathematically Valid" badge even when the backend explicitly rejected the proof.

## Decision Drivers

* **Cryptographic Truthfulness:** The frontend must NEVER display positive verification feedback unless mathematically proven by the backend.
* **Strict Boolean Identity:** Elimination of loose truthy checks on undefined properties.
* **Granular Semantic States:** Distinct visual indicators for active, expired, revoked, and failed credentials.

## Decision

1. **Strict Identity Verification:** All verification logic enforces strict boolean identity:
   ```typescript
   const isVerified = response.verified === true;
   ```
   Under no circumstances may an `undefined` or `null` property evaluate to `true`.
2. **Distinct Semantic Badges:**
   - `VERIFIED & ACTIVE`: `valid === true && status === 'ACTIVE'`
   - `CREDENTIAL REVOKED`: `status === 'REVOKED'`
   - `CREDENTIAL EXPIRED`: `status === 'EXPIRED'`
   - `PREDICATE SATISFIED`: `verified === true` (from `/v2/zkp/*`)
   - `PROOF REJECTED`: `verified === false` (from `/v2/zkp/*`)
   - `NOT RECOGNIZED`: HTTP 404 response
3. **Automated Regression Testing:** An explicit unit test suite will assert that `{ verified: false }` and malformed payloads always render the red "PROOF REJECTED" banner.

## Consequences

### Positive
- Total elimination of false-positive cryptographic feedback.
- Protects verifiers from relying on invalid, forged, or expired credentials.
- Restores integrity and compliance alignment to the platform.

### Negative
- None. Truthfulness is a non-negotiable security invariant.
