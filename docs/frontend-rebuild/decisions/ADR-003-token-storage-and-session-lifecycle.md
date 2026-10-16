# ADR-003: Token Storage and Session Lifecycle

## Status

Accepted

## Context

The backend is an OAuth2 Resource Server that verifies incoming requests using `JwtAuthenticationFilter` and `ManagedJwtDecoderProvider`. It requires signed RS256/PS256 JWTs with specific claims (`sub`, `roles`, `iss: https://auth.zkdoc.internal`, `aud: zkdoc-api`, `kid`).

The backend does not provide an in-band username/password login endpoint or session cookie mechanism. The legacy frontend had introduced a severe vulnerability by fabricating unsigned mock tokens (`demo-signature-for-development-eval`) claiming administrative roles. Furthermore, storing JWTs in `localStorage` creates severe XSS token-theft vulnerabilities.

## Decision Drivers

* **Security:** Must eliminate permanent token persistence in `localStorage`.
* **Zero Mock Tokens:** Must disallow fabricated fake signatures that bypass security standards.
* **Developer Ergonomics:** Must provide an interactive Developer JWT Connect modal for testing with real tokens.
* **Session Lifecycle:** Must proactively monitor token expiration (`exp`) and terminate sessions cleanly.

## Decision

1. **Storage Location:** Authentication tokens are stored primarily in **in-memory Zustand state**. An optional opt-in allows storage in `sessionStorage` strictly to survive manual tab refreshes during testing. Tokens are **NEVER written to `localStorage`**.
2. **Developer Connect Interface:** Replaces fake login endpoints with a dedicated "Connect Developer JWT" modal. Operators paste a signed JWT issued by the internal IdP or testing key utility.
3. **Claim Decoding:** The client decodes the JWT payload to display the subject (`sub`), role badges (`roles`), and expiration time (`exp`) in the UI header.
4. **Session Watcher:** A background timer triggers a warning modal 60 seconds before token expiration and executes an automated logout when `exp` is reached.
5. **No Refresh Tokens:** Because the backend does not currently support refresh tokens or server-side session revocation, token renewal requires re-entering a valid token.

## Consequences

### Positive
- Minimizes XSS blast radius: closing the tab wipes the token completely.
- Enforces cryptographic discipline by requiring real signed tokens matching backend keys.
- Clear, transparent session status displayed in the UI header.

### Negative
- Users must reconnect if they open a separate browser window or if the in-memory tab state is cleared.
