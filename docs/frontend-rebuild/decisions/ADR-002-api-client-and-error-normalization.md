# ADR-002: API Client Architecture and Error Normalization

## Status

Accepted

## Context

The backend Spring Boot REST API handles document uploads, credential issuance, cryptographic key rotation, and Zero-Knowledge predicate verification. Errors are serialized using a unified `ApiErrorResponse` structure:
```json
{
  "timestamp": "ISO-8601",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/...",
  "correlationId": "uuid-v4",
  "validationErrors": { "field": "constraint message" }
}
```

The legacy frontend had severe flaws: it looked for an obsolete `payload.code` attribute, omitted correlation IDs, and lacked centralized 401 session expiry handling. Furthermore, automatic retries on state-changing cryptographic operations could cause accidental double-issuance or key state drift.

## Decision Drivers

* **Correlation Tracing:** Must propagate `X-Correlation-Id` on all requests for end-to-end auditability.
* **Contract Fidelity:** Must normalize errors directly from the backend's `ApiErrorResponse`.
* **Idempotency & Safety:** Must disallow automatic retries on cryptographic mutations.
* **Session Interception:** Must cleanly handle 401 responses and trigger session renewal.

## Decision

1. We will implement a typed fetch client wrapper (`src/api/client.ts`) without external heavyweight HTTP libraries (like Axios).
2. The client will generate and attach `X-Correlation-Id: <UUIDv4>` on every outgoing HTTP request.
3. Errors will be normalized into an `ApiError` class exposing `status`, `correlationId`, `message`, and `validationErrors`.
4. Idempotent GET queries will be managed by **TanStack Query v5** with 2 automatic retries; mutations (POST/PUT/DELETE) will have **0 automatic retries**.
5. A 401 response will trigger immediate session clearing in the Zustand auth store and open the session renewal dialog.

## Consequences

### Positive
- Unified, predictable error handling across all forms and views.
- Immediate client-side association between backend field errors (`validationErrors`) and React Hook Form inputs.
- Elimination of duplicate cryptographic commits caused by blind HTTP retries.
- Full distributed tracing correlation between browser errors and Spring Boot backend logs.

### Negative
- Manual configuration of fetch options compared to all-inclusive libraries like Axios.
