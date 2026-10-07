# Architecture Specification: ZKP Proving Boundary & Holder Lifecycle

## 1. Executive Summary

Phase 6B completes the holder credential lifecycle and establishes the cryptographic proving boundary between the holder's witness store and zero-knowledge proof generation. This architecture strictly isolates private witness parameters ($v$, $r$) from the backend and verifiers, ensuring zero-custody and BOLA/IDOR protection.

## 2. Threat Modeling & Security Invariants

| Vector | Threat | Mitigation / Control |
| :--- | :--- | :--- |
| **BOLA / IDOR** | Holder A requests witness material belonging to Holder B. | `HolderCredentialService.findByIdAndHolder` enforces strict subject ownership matching against authenticated caller (`Authentication.getName()`). |
| **Information Disclosure** | Blinding factors or plaintext values logged or leaked in error dumps. | `HolderProofContext.toString()` explicitly redacts `blindingFactor` and `attributeValue` as `[REDACTED]`. The context record is never returned via public APIs or REST endpoints. |
| **Lifecycle Inconsistency** | Prover generates a proof using a revoked or suspended credential. | `DefaultHolderProvingMaterialAccess` verifies both the holder status (`ACTIVE`) and the issuer-side authoritative credential status (`ACTIVE`). Fails closed on non-active states. |
| **Witness Tampering** | Adversary injects invalid opening tuple $(v', r')$. | Cryptographic continuity verification computes $C = vG + rH$ using `PedersenCommitmentService.verifyOpening` prior to returning the proof context. Mismatches abort immediately. |
| **Memory Retention** | Witness parameters persist indefinitely in memory. | `HolderProofContext` implements `javax.security.auth.Destroyable` enabling deterministic zeroization of sensitive arrays. |

## 3. Proving Boundary Flow

```mermaid
sequenceDiagram
    autonumber
    actor Holder as Authenticated Holder
    participant API as Holder Credential API
    participant SVC as HolderCredentialService
    participant BOUNDARY as HolderProvingMaterialAccess
    participant STORE as Credential & Commitment Stores
    participant WITNESS as HolderProvingMaterialStore
    participant CRYPTO as PedersenCommitmentService

    Holder->>API: POST /api/holder/credentials/{id}/suspend
    API->>SVC: suspend(id, holderId)
    SVC-->>API: Updated Status (SUSPENDED)
    API-->>Holder: 200 OK

    Holder->>BOUNDARY: resolveProofContext(holderId, credId, attribute)
    BOUNDARY->>SVC: findByIdAndHolder(credId, holderId)
    BOUNDARY->>STORE: verify active authoritative credential
    BOUNDARY->>STORE: get AttributeCommitment (C, attestation)
    BOUNDARY->>WITNESS: get AttributeOpeningSecret (v, r)
    BOUNDARY->>CRYPTO: verifyOpening(v, r, C)
    Note over BOUNDARY,CRYPTO: C ?= vG + rH (Curve25519)
    CRYPTO-->>BOUNDARY: valid
    BOUNDARY-->>Holder: HolderProofContext (redacted toString, destroyable)
```

## 4. Phase Boundary Compliance

- No Bulletproof range proofs, constraint gadgets, or nonces are generated or exposed in this phase.
- Only proving context provisioning and holder lifecycle state transitions are implemented.
