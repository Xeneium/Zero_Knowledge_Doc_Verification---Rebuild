# Phase 2 — Comprehensive User Journey Specifications
## Zero-Knowledge Document Verification Platform

---

## 1. Overview & Architectural Principles

This document defines the detailed, step-by-step user journeys for the **Zero-Knowledge Document Verification Platform**, completing the journey mapping requirements of **Phase 2: UX Research & User Journey Planning**.

### 1.1 Key Journey Mandates
1. **Independent Verification Channels:** Journey B (QR-based verification) and Journey C (Direct document verification) are completely autonomous. A user with a physical QR code never needs to provide a raw PDF, and a user with a raw PDF never needs to scan a QR code.
2. **Honest System Feedback:** Each journey specifies distinct system feedback for cryptographic success, tampering detection, expired credentials, revoked credentials, network dropouts, and unauthorized attempts.
3. **Deterministic Error Recovery:** Every error state provides an immediate, one-click recovery path that preserves prior user input wherever safe.

---

## 2. Journey Index

- [Journey A — Institutional Document Registration & Signing](#journey-a--institutional-document-registration--signing)
- [Journey B — Public QR-Code Document Verification](#journey-b--public-qr-code-document-verification)
- [Journey C — Public Direct Document Verification (Independent Modality)](#journey-c--public-direct-document-verification-independent-modality)
- [Journey D — Operator Authentication & Session Management](#journey-d--operator-authentication--session-management)
- [Journey E — Holder Credential Wallet & Lifecycle Management](#journey-e--holder-credential-wallet--lifecycle-management)
- [Journey F — Platform Trust Administration & Cryptographic Audit](#journey-f--platform-trust-administration--cryptographic-audit)
- [Journey G — Zero-Knowledge Predicate Range Proof Verification](#journey-g--zero-knowledge-predicate-range-proof-verification)

---

## Journey A — Institutional Document Registration & Signing

### A.1 Overview & Metadata
- **Objective:** Securely upload an official organization document (PDF, PNG, JPG, JSON), calculate its SHA-256 hash digest, sign the digest with the institution's active RSA private key, persist the registered artifact, and generate a downloadable verification capability reference and QR code.
- **Intended User:** Authorized Institutional Issuer (`ROLE_ISSUER` or `ROLE_ADMIN`).
- **Preconditions:** User has an active, authenticated session with `ROLE_ISSUER` or `ROLE_ADMIN`. Active RSA signing key is registered in the platform key registry.
- **Entry Point:** Navigation Bar -> `Issuer Workspace` (`/issuer`) -> `Register Document` tab.

### A.2 Mermaid Flowchart

```mermaid
flowchart TD
    Start(["Issuer navigates to /issuer"]) --> Dropzone["View Document Upload Dropzone"]
    Dropzone --> SelectFile["Select or Drag-and-Drop Document File"]
    SelectFile --> ClientValidate{"Client Validation: Size <= 25MB and Type Supported?"}
    
    ClientValidate -- "No (Size > 25MB or Bad Type)" --> ClientError["Show Validation Error Banner: Retain file picker"]
    ClientError --> SelectFile
    
    ClientValidate -- "Yes" --> ShowPreview["Display File Metadata: Name, Size, MIME Type"]
    ShowPreview --> ClickSubmit["Click 'Register and Sign Document'"]
    
    ClickSubmit --> UploadingState["Display Upload Progress Bar (0% to 100%)"]
    UploadingState --> BackendProcess["POST /api/documents (Multipart payload)"]
    
    BackendProcess --> ServerOutcome{"Backend Processing Outcome"}
    
    ServerOutcome -- "Success (HTTP 201)" --> SuccessState["Render Registration Receipt Card"]
    ServerOutcome -- "Duplicate / Conflict (HTTP 409)" --> ConflictError["Display Conflict Notice with existing Document ID"]
    ServerOutcome -- "Signing Key Inactive (HTTP 422)" --> KeyInactiveError["Display Key Rotation Warning: Contact Admin"]
    ServerOutcome -- "Network Timeout / Server Error" --> NetworkError["Display Retry Banner: 'Upload interrupted, retry?'"]
    
    SuccessState --> ViewReceipt["Inspect Document ID, SHA-256 Digest, and RSA Key Fingerprint"]
    ViewReceipt --> Actions["Action Choices: Download QR Code / Copy Reference / Register Another"]
```

### A.3 Step-by-Step Specification

| Step | User Action | Interface Response | Backend Operation | Technical Details |
| :---: | :--- | :--- | :--- | :--- |
| **A.1** | Navigates to `/issuer`. | Renders Issuer Workspace with active role badge (`ISSUER`) and tabs: `Register Document`, `Issue Credential`, `Credential Lifecycle`, `Activity Log`. | None (Client routing). | `RequireAuth` and `RequireRole` enforce session claims. |
| **A.2** | Drags a 5.2 MB PDF diploma into dropzone (or clicks to open system file picker). | Dropzone border highlights cyan; displays file icon, filename (`diploma-2026.pdf`), formatted size (`5.2 MB`), and MIME type (`application/pdf`). | None (Client-side FileReader). | Max boundary: 25 MB (`26,214,400 bytes`). Supported: `application/pdf`, `image/png`, `image/jpeg`, `application/json`. |
| **A.3** | Clicks `"Register & Sign Document"` button. | Button transitions to loading spinner (`Registering & Computing Digest...`); dropzone disables to prevent double-click; determinate progress bar advances. | `POST /api/documents`<br>Headers: `Authorization: Bearer <JWT>`, `Content-Type: multipart/form-data`. | Backend computes SHA-256 digest, retrieves active RSA-2048 key, signs hash, writes to database. |
| **A.4** | Backend returns HTTP 201 Created. | Screen transitions smoothly to the **Document Registration Receipt Card**; displays success checkmark, Document UUID, truncated SHA-256 hash (`0x8f4c...3e12`), and embedded QR code image. | `GET /api/documents/{id}/qr`<br>Fetches rendered PNG QR matrix. | Receipt includes one-click buttons: `[Download QR PNG]`, `[Copy Verification URL]`, and `[Register Another Document]`. |
| **A.5** | Clicks `"Download QR PNG"`. | Browser downloads `zkdoc-qr-{documentId}.png`. | None (Blob download). | Matrix encodes canonical JSON capability payload. |

### A.4 Failure Paths & Recovery Behaviors
- **File Exceeds 25 MB:** Dropzone rejects file instantly before upload begins; displays inline warning: *"File size (31.4 MB) exceeds the maximum allowed limit of 25.0 MB. Please compress the document before uploading."* File input resets cleanly.
- **Network Interruption During Upload:** Progress bar pauses; displays red alert: *"Upload interrupted due to network disconnection. [Retry Upload]"*. Clicking Retry re-attempts `POST /api/documents` with the currently selected file.
- **Issuer Signing Key Inactive/Expired (HTTP 422):** Displays warning: *"Your organization's active signing key has expired or is suspended. Please contact your platform administrator to rotate the signing key."*

### A.5 Privacy & Security Controls
- Uploaded files are streamed directly to backend encrypted storage; document contents are never logged or stored in `localStorage` or `sessionStorage`.
- Only authorized users with `ROLE_ISSUER` or `ROLE_ADMIN` can initiate document registration.

---

## Journey B — Public QR-Code Document Verification

### B.1 Overview & Metadata
- **Objective:** Enable any relying party or member of the public to scan a printed or on-screen QR verification code using their device camera (or upload a QR image), decode the verification capability reference, submit it to the backend, and view an authoritative authenticity certificate.
- **Intended User:** Public Document Verifier (Anonymous / Unauthenticated).
- **Preconditions:** Device has an operational camera (or user provides QR image file). Device has internet connectivity to reach backend verification API.
- **Entry Point:** Navigation Bar -> `Verify Document` -> Tab 1 (`Scan QR Code`). Also accessible directly via deep-link `/verify/:token`.

### B.2 Mermaid Flowchart

```mermaid
flowchart TD
    Start(["User opens /verify"]) --> OpenTab["Select Tab 1: 'Scan QR Code'"]
    OpenTab --> CheckPerm{"Camera Permission Status"}
    
    CheckPerm -- "Prompt / Needs Request" --> RequestCam["Click 'Start Camera Scanner'"]
    RequestCam --> BrowserPrompt{"Browser Camera Dialog"}
    BrowserPrompt -- "Granted" --> StreamVideo["Activate Viewfinder Video Stream"]
    BrowserPrompt -- "Denied / Blocked" --> PermDenied["Show Permission Recovery Instructions + Fallback to Image Upload"]
    
    CheckPerm -- "No Camera Hardware" --> NoHardware["Display 'No Camera Detected' + Switch to File / Token Tab"]
    
    StreamVideo --> ScanMatrix["Point Camera at Verification QR Code"]
    ScanMatrix --> DecodeQR{"ZXing Decodes Matrix?"}
    
    DecodeQR -- "Matrix Decoded" --> ParsePayload{"Validate Payload Format"}
    ParsePayload -- "Valid Capability Token" --> StopCamera["Stop Camera Stream Immediately"]
    ParsePayload -- "Malformed Payload" --> BadPayload["Show Warning: 'Unrecognized QR Format' + Resume Scanning"]
    BadPayload --> StreamVideo
    
    StopCamera --> SubmitToken["GET /api/public/verify/capability/{token}"]
    SubmitToken --> VerifyResponse{"Backend Verification Response"}
    
    VerifyResponse -- "Authentic (HTTP 200)" --> ShowAuthentic["Display 'Authentic & Verified' Card with Issuer Name and Date"]
    VerifyResponse -- "Revoked (HTTP 200 / isRevoked)" --> ShowRevoked["Display 'Credential Revoked' Alert with Revocation Reason"]
    VerifyResponse -- "Tampered / Invalid (HTTP 404/422)" --> ShowTampered["Display 'Verification Failed: Unrecognized Token'"]
    VerifyResponse -- "Service Offline (HTTP 503)" --> ShowOffline["Display Service Unavailable Banner + Retry Button"]
    
    ShowAuthentic --> Done(["Verifier inspects proof details or clicks 'Verify Another'"])
```

### B.3 Step-by-Step Specification

| Step | User Action | Interface Response | Backend Operation | Technical Details |
| :---: | :--- | :--- | :--- | :--- |
| **B.1** | Navigates to `/verify`. | Renders Public Verification Hub defaulting to **Tab 1: Scan QR Code**. Displays camera viewfinder placeholder with `"Start Camera Scanner"` CTA. | None (Client routing). | No authentication required; public endpoint. |
| **B.2** | Clicks `"Start Camera Scanner"`. | Browser displays permissions prompt (*"Allow ZKDoc to use your camera?"*). Viewfinder shows animated scanning frame. | None (Browser MediaDevices API). | Constraints: `{ video: { facingMode: "environment", width: { ideal: 1280 } } }`. |
| **B.3** | Holds printed diploma QR code in front of lens. | ZXing Browser detects QR matrix, beeps subtly (if audio enabled), stops camera hardware stream immediately, and displays loading indicator: *"Verifying capability reference..."*. | Decodes JSON: `{"documentId":"...","token":"cap_8f92e4","capability":"read"}`. | Camera tracks are stopped immediately (`track.stop()`) to release hardware and preserve battery. |
| **B.4** | Frontend queries verification API. | Central card updates with verification spinner. | `GET /api/public/verify/capability/{token}` | Queries `PublicVerificationController`. Backend verifies capability signature, expiration, and document existence. |
| **B.5** | Backend returns verified response. | Viewfinder collapses; renders **Verification Result Card** in emerald green: displays checkmark badge `"Authentic & Verified"`, Issuing Authority name, Document Title, Registration Date, and RSA Key Fingerprint. Document body text remains hidden. | None. | Document contents remain strictly confidential. Only metadata essential to trust is disclosed. |
| **B.6** | Clicks `"Verify Another Document"`. | Screen resets to Tab 1 with viewfinder ready for next scan. | None. | Verification state cleared from active view. |

### B.4 Failure Paths & Recovery Behaviors
- **Camera Permission Denied:** Viewfinder displays accessible warning banner: *"Camera access was denied in your browser settings. To verify using a QR code, you can upload an image of the QR code below or enter the verification reference token manually."* An immediate `"Upload QR Image"` button and `"Enter Reference Manually"` button are presented.
- **Invalid or Expired QR Token:** Result card renders in amber/red: *"Unable to verify this document. The verification token is either expired or does not correspond to an authentic registered document. [Scan Another Code]"*.
- **Revoked Credential:** Result card renders with shield alert: *"Warning: This document was formally revoked by the issuing authority on October 12, 2026. Reason: Replaced by updated credential."*

---

## Journey C — Public Direct Document Verification (Independent Modality)

### C.1 Overview & Metadata
- **Objective:** Enable a verifier holding an original document file (e.g. PDF diploma, transcript, or legal contract) to verify its authenticity and integrity by uploading the file directly, without requiring the document to possess an embedded QR code.
- **Intended User:** Public Document Verifier (Anonymous / Unauthenticated).
- **Preconditions:** Verifier possesses the digital file. File size is within the 25 MB limit.
- **Entry Point:** Navigation Bar -> `Verify Document` -> Tab 2 (`Upload Document File`).
- **CRITICAL INDEPENDENCE RULE:** This journey is completely self-contained. The interface must never ask the user to scan a QR code, provide a QR payload, or possess a camera.

### C.2 Mermaid Flowchart

```mermaid
flowchart TD
    Start(["User opens /verify"]) --> SelectTab["Click Tab 2: 'Upload Document File'"]
    SelectTab --> Explain["Read Guidance: 'No QR code required. Upload your original file to verify integrity.'"]
    Explain --> DropDoc["Select or Drag-and-Drop Original Document"]
    
    DropDoc --> ValidateFile{"Client Validation: Size <= 25MB?"}
    ValidateFile -- "Exceeds 25MB" --> SizeError["Show Error: 'File exceeds 25 MB limit. Please compress file.'"]
    SizeError --> DropDoc
    
    ValidateFile -- "Valid" --> ReadyState["Show File Card: Name, Size, MIME Type"]
    ReadyState --> ClickVerify["Click 'Verify Document Hash'"]
    
    ClickVerify --> UploadState["Show Upload & Cryptographic Hashing Progress"]
    UploadState --> BackendCheck["POST /api/public/verify/upload (Multipart file)"]
    
    BackendCheck --> ResultChoice{"Backend Verification Verdict"}
    
    ResultChoice -- "Digest Match & Signature Valid" --> DirectAuthentic["Display 'Authentic & Untampered' Certificate"]
    ResultChoice -- "Digest Mismatch / Not Found" --> DirectTampered["Display 'Verification Failed: Unregistered or Modified'"]
    ResultChoice -- "Document Revoked" --> DirectRevoked["Display 'Document Revoked by Issuing Organization'"]
    ResultChoice -- "Server Error (HTTP 500)" --> DirectServerError["Display 'Verification Service Temporarily Unavailable'"]
    
    DirectAuthentic --> InspectDetails["Optionally Expand 'Cryptographic Proof Details' (SHA-256 Digest)"]
    DirectAuthentic --> VerifyNext["Click 'Verify Another Document'"]
```

### C.3 Step-by-Step Specification

| Step | User Action | Interface Response | Backend Operation | Technical Details |
| :---: | :--- | :--- | :--- | :--- |
| **C.1** | Navigates to `/verify` and clicks **Tab 2: "Upload Document File"**. | Active tab switches to document dropzone. Clear informational banner states: *"You can verify any official document issued through this platform by uploading its original digital file. No QR code is required."* | None. | Distinct URL state: `/verify?mode=upload`. |
| **C.2** | Selects `degree-certificate-official.pdf` (3.8 MB). | Dropzone renders selected file chip with PDF badge, file size, and remove `(X)` button. Action button `"Verify Document Hash"` activates. | None (Client validation). | Supported file types: PDF, PNG, JPG, JSON. Bounds: 25 MB. |
| **C.3** | Clicks `"Verify Document Hash"`. | Button switches to loading state: *"Uploading & Computing Digest..."*; progress indicator pulses. | `POST /api/public/verify/upload`<br>Payload: `multipart/form-data; file=degree-certificate-official.pdf`. | Backend computes SHA-256 digest in memory, compares digest against registered database hashes, verifies RSA signature. |
| **C.4** | Backend matches hash against registered record (HTTP 200). | Screen transitions to the **Direct Verification Result Card** with green shield icon: *"Document Authentic & Untampered"*. Displays Issuer Name, Issuance Date, and Document ID. | None. | Does not store or retain the uploaded file on the server. Verification is performed statelessly against digests. |
| **C.5** | Clicks `"View Cryptographic Proof Details"` accordion. | Accordion smoothly expands to reveal: Computed SHA-256 Hash (`0x7a9c...81f4`), Signing Key ID (`key-inst-2026`), and Timestamp. | None. | Progressive disclosure: keeps raw cryptographic details accessible for technical users without cluttering primary view. |

### C.4 Failure Paths & Recovery Behaviors
- **Document Content Modified (Digest Mismatch):** Renders high-visibility warning card: *"Integrity Verification Failed: The uploaded document does not match any registered authentic records. This may indicate the file was edited, compressed, re-saved, or originated from an unauthorized source. [Try Another Document]"*.
- **Unsupported File Format:** If user selects a `.docx` or `.exe` file, dropzone displays immediate client-side error: *"Unsupported file format. Please upload an original PDF, PNG, JPG, or JSON document."*

---

## Journey D — Operator Authentication & Session Management

### D.1 Overview & Metadata
- **Objective:** Enable institutional issuers, credential holders, and platform administrators to establish an authenticated session by providing a valid asymmetric RSA-signed JWT token, inspect live decoded token claims prior to connection, and access protected workspaces.
- **Intended User:** Authorized Operators (`ISSUER`, `HOLDER`, `ADMIN`).
- **Preconditions:** Operator possesses a valid, unexpired JWT issued by the organization's authentication authority.
- **Entry Point:** Navigation Bar -> `Operator Login` (`/login`).

### D.2 Mermaid Flowchart

```mermaid
flowchart TD
    Start(["Operator navigates to /login"]) --> LoginForm["View Token Input Textarea"]
    LoginForm --> PasteToken["Paste Bearer JWT Token"]
    
    PasteToken --> ParseClaims{"Client-Side RFC 8725 JWT Parsing"}
    ParseClaims -- "Unsecured (alg: none)" --> BlockNone["Strict Reject: 'Unsecured alg:none forbidden'"]
    ParseClaims -- "Symmetric (alg: HS*)" --> BlockHS["Strict Reject: 'Symmetric HS* tokens rejected'"]
    ParseClaims -- "Expired (exp <= now)" --> BlockExp["Strict Reject: 'Token expired at [timestamp]'"]
    ParseClaims -- "Malformed JSON / Segments" --> BlockFormat["Show Error: 'Invalid JWT structure'"]
    
    ParseClaims -- "Valid Asymmetric Token" --> LivePreview["Display Live Decoded Claims Card: Subject, Roles, Issuer, Expiry"]
    LivePreview --> EnableSubmit["Enable 'Connect Session' Button"]
    
    EnableSubmit --> ClickConnect["Click 'Connect Session'"]
    ClickConnect --> SaveSession["Store Token in sessionStorage ('zkdoc_auth_session')"]
    SaveSession --> CheckRedirect{"Examine returnUrl Parameter"}
    
    CheckRedirect -- "Safe Relative Path (e.g. /issuer)" --> RedirectTarget["Redirect to Requested Protected Workspace"]
    CheckRedirect -- "Unsafe / External URL (e.g. //evil.com)" --> RedirectSafe["Redirect to Default Role Workspace"]
    
    RedirectTarget --> WorkspaceReady(["Operator active in authorized workspace"])
```

### D.3 Step-by-Step Specification

| Step | User Action | Interface Response | Backend Operation | Technical Details |
| :---: | :--- | :--- | :--- | :--- |
| **D.1** | Navigates to `/login` (or redirected from `/issuer` with `?returnUrl=%2Fissuer`). | Renders Operator Login Page with Zero-Trust notice: *"Enter an RSA-signed Bearer JWT to access administrative workspaces."* Token textarea is displayed. | None. | Complies with RFC 8725 JWT Best Current Practices. |
| **D.2** | Pastes a valid RS256 JWT into the textarea. | As the user types/pastes, client parser decodes header and payload in real-time. Displays **Decoded Token Claims Preview**: Subject (`issuer-dept-1`), Roles (`ROLE_ISSUER`), Issuer (`https://auth.zkdoc.internal`), Valid Until (`in 2 hours`). Button `"Connect Session"` enables. | None (Pure client decoding). | Rejects `alg: "none"` or `HS256`. Validates `exp` against client epoch time. |
| **D.3** | Clicks `"Connect Session"`. | Token is saved to `sessionStorage`. AuthStore broadcasts session established event. User is redirected to `/issuer`. | None (Client storage). | `sessionStorage.setItem('zkdoc_auth_session', JSON.stringify({ token, user }))`. Tab-isolated. |
| **D.4** | Navigates to `/issuer`. | Header displays active user badge: `issuer-dept-1 [ISSUER]`. Protected route renders issuer dashboard. | Protected API calls attach `Authorization: Bearer <token>`. | `RequireAuth` and `RequireRole` pass without friction. |
| **D.5** | Clicks `"Disconnect Session"` in header. | Session token is removed from `sessionStorage`; DOM event `zkdoc:auth:cleared` fires; in-memory store states clear; redirects to `/login`. | None. | Clean teardown prevents cached data leakage on shared computers. |

### D.4 Security Protections
- **Open Redirect Prevention:** If `returnUrl` contains `//evil.com` or `javascript:`, the login controller detects the malicious pattern and forces redirection to the default safe workspace `/holder` or `/issuer`.
- **Private Key Submission Shield:** If an operator accidentally pastes an RSA private key into the token textarea, the parser rejects it: *"Invalid token: Private keys must never be provided as session tokens."*

---

## Journey E — Holder Credential Wallet & Lifecycle Management

### E.1 Overview & Metadata
- **Objective:** Enable a credential holder to view their digital credentials in a personal wallet, inspect claim attributes, verify issuer digital signatures, generate Bulletproofs zero-knowledge range proofs for selective disclosure, and manage credential status (self-suspension).
- **Intended User:** Credential Holder (`ROLE_HOLDER` or `ROLE_ADMIN`).
- **Preconditions:** Holder has an active authenticated session with `ROLE_HOLDER`.
- **Entry Point:** Navigation Bar -> `Holder Workspace` (`/holder` or `/credentials`).

### E.2 Mermaid Flowchart

```mermaid
flowchart TD
    Start(["Holder opens /holder"]) --> LoadWallet["GET /api/holder/credentials"]
    LoadWallet --> RenderList["Display Credential Wallet Cards with Status Badges"]
    
    RenderList --> ActionChoice{"Holder Interaction"}
    
    ActionChoice -- "View Details" --> OpenModal["Click Credential -> Open Credential Detail Modal"]
    OpenModal --> InspectClaims["Inspect Issuer Name, Issue Date, and Validated Claims"]
    InspectClaims --> FetchHistory["GET /api/credentials/{id}/lifecycle/history"]
    FetchHistory --> ShowTimeline["Render Audit Timeline: Issued -> Active"]
    
    ActionChoice -- "Self-Suspend" --> ClickSuspend["Click 'Suspend Credential'"]
    ClickSuspend --> ConfirmModal["Render Confirmation Dialog: 'Temporarily disable verification?'"]
    ConfirmModal --> ExecuteSuspend["POST /api/holder/credentials/{id}/suspend"]
    ExecuteSuspend --> UpdateStatus["Update Credential Badge to 'SUSPENDED' in Real-Time"]
    
    ActionChoice -- "Generate ZKP Proof" --> OpenZKP["Click 'Generate Privacy Proof'"]
    OpenZKP --> NavZKP["Transition to Journey G (ZKP Predicate Proof)"]
```

### E.3 Step-by-Step Specification

| Step | User Action | Interface Response | Backend Operation | Technical Details |
| :---: | :--- | :--- | :--- | :--- |
| **E.1** | Navigates to `/holder`. | Renders Holder Workspace. Automatically fetches credentials. Displays wallet metrics: Total Credentials (`3`), Active (`2`), Suspended (`1`). | `GET /api/holder/credentials` | Endpoint queries `HolderCredentialController`. Returns list of credential cards. |
| **E.2** | Clicks on `"Bachelor of Computer Science"` credential card. | Opens **Holder Credential Detail Modal**. Focus is trapped in modal. Displays full claim metadata, issuing authority, and cryptographic signature validation status. | `GET /api/credentials/{id}/lifecycle/history` | Fetches lifecycle history events for timeline display. |
| **E.3** | Clicks `"Suspend Credential"`. | Opens confirmation dialog: *"Are you sure you want to suspend this credential? Third parties will not be able to verify it until you reactivate it."* | None. | Explicit user confirmation required. |
| **E.4** | Confirms suspension. | Modal closes; credential status badge updates to amber `SUSPENDED`; toast notification confirms: *"Credential suspended successfully"*. | `POST /api/holder/credentials/{id}/suspend` | Backend updates credential status in database. |

---

## Journey F — Platform Trust Administration & Cryptographic Audit

### F.1 Overview & Metadata
- **Objective:** Enable a platform administrator to inspect active RSA public keys in the trust registry, register new institutional public keys with strict zero-private-key defense, execute key rotation, and verify the cryptographic integrity of the Merkle audit event log chain.
- **Intended User:** Platform Administrator (`ROLE_ADMIN`).
- **Preconditions:** Active authenticated session with `ROLE_ADMIN`.
- **Entry Point:** Navigation Bar -> `Administration` (`/administration`) and `Audit Logs` (`/audit-logs`).

### F.2 Mermaid Flowchart

```mermaid
flowchart TD
    Start(["Admin navigates to /administration"]) --> Overview["View System Health & Key Registry Cards"]
    Overview --> AdminAction{"Select Admin Action"}
    
    AdminAction -- "Register New Public Key" --> OpenKeyModal["Click 'Register Key'"]
    OpenKeyModal --> PasteKey["Paste Public Key PEM"]
    PasteKey --> GuardCheck{"Adversarial Check: Contains 'BEGIN PRIVATE KEY'?"}
    GuardCheck -- "Yes (Dangerous Private Key Detected)" --> InterceptKey["STRICT BLOCK: Show Alert 'Private keys strictly forbidden'"]
    GuardCheck -- "No (Valid Public Key)" --> SubmitKey["POST /api/admin/keys"]
    SubmitKey --> KeySuccess["Refresh Key Registry Table"]
    
    AdminAction -- "Audit Log Merkle Verification" --> NavAudit["Navigate to /audit-logs"]
    NavAudit --> LoadEvents["GET /api/admin/audit/events"]
    LoadEvents --> ClickVerifyChain["Click 'Verify Audit Chain Integrity'"]
    ClickVerifyChain --> RunChain["POST /api/admin/audit/verify-integrity"]
    RunChain --> ChainOutcome{"Merkle Hash Chain Intact?"}
    ChainOutcome -- "Valid" --> GreenChain["Display 'Hash Chain Verified: 1,420 events untampered'"]
    ChainOutcome -- "Broken" --> RedChain["CRITICAL ALERT: 'Tampering Detected at Block #412'"]
```

### F.3 Step-by-Step Specification

| Step | User Action | Interface Response | Backend Operation | Technical Details |
| :---: | :--- | :--- | :--- | :--- |
| **F.1** | Navigates to `/administration`. | Renders System Overview, Active Key Registry Table, and Recovery Status Card. | `GET /api/admin/keys`, `GET /api/admin/recovery/status` | Enforces `ROLE_ADMIN` access guard. |
| **F.2** | Clicks `"Register New Key"`. | Opens **Register Key Modal**. Displays Key ID input, Algorithm selector (`RS256`, `PS256`), and Public Key PEM textarea. | None. | Field labels explicitly specify `"PUBLIC KEY (X.509 PEM)"`. |
| **F.3** | Accidentally pastes an RSA private key containing `-----BEGIN RSA PRIVATE KEY-----`. | Instant red alert blocks submission: *"Security Violation: You have pasted a PRIVATE KEY. The platform trust registry accepts ONLY PUBLIC KEYS. Never disclose your private key."* Submit button is hard-disabled. | None (Client-side regex guard). | Pattern: `/-----BEGIN (RSA )?PRIVATE KEY-----/`. Prevents catastrophic security leakage. |
| **F.4** | Corrects input to valid public key PEM and submits. | Modal closes; table updates showing new key in `ACTIVE` status with SHA-256 fingerprint. | `POST /api/admin/keys` | Writes to `JdbcKeyRegistry`. |
| **F.5** | Navigates to `/audit-logs` and clicks `"Verify Hash Chain Integrity"`. | Audit chain verification card animates; displays verified badge: *"Audit Log Chain Integrity Confirmed: All Merkle block links verified without tampering."* | `POST /api/admin/audit/verify-integrity` | Backend re-calculates Merkle root across all audit records. |

---

## Journey G — Zero-Knowledge Predicate Range Proof Verification

### G.1 Overview & Metadata
- **Objective:** Enable a credential holder to generate a zero-knowledge range proof (e.g., proving `age >= 21` or `income >= 50000`) using Weavechain Bulletproofs over cryptographic commitments, and allow any relying party to verify the mathematical validity of the claim without revealing the holder's exact age or income.
- **Intended User:** Holder (generating proof) and Public Verifier (verifying proof).
- **Preconditions:** Holder possesses a verifiable credential with numerical attributes; backend `zkdoc-zkp` module is active.
- **Entry Point:** Holder: `/holder` -> `Privacy Proofs`. Verifier: `/verify` -> Tab 4 (`ZKP Predicates`) or direct route `/predicate-verification`.

### G.2 Mermaid Flowchart

```mermaid
flowchart TD
    subgraph Holder Phase [Holder: Generate Proof]
        HStart(["Holder on /holder"]) --> SelectCred["Select Credential with Numerical Claim"]
        SelectCred --> ChoosePredicate["Select Predicate Rule (e.g. 'Age >= 21')"]
        ChoosePredicate --> GenProof["Click 'Generate Bulletproofs Range Proof'"]
        GenProof --> PostGen["POST /api/holder/credentials/proofs/generate"]
        PostGen --> ReceiveProof["Receive Proof Object: Commitment, Proof Bytes, Public Parameters"]
        ReceiveProof --> ExportProof["Click 'Copy Proof JSON' or 'Generate Proof QR'"]
    end

    subgraph Verifier Phase [Verifier: Verify Proof]
        VStart(["Verifier on /verify (Tab 4)"]) --> PasteProof["Paste Proof JSON or Scan Proof QR"]
        PasteProof --> ValidateProofJSON{"Valid JSON & Commitment Present?"}
        ValidateProofJSON -- "No" --> SyntaxError["Show Error: 'Invalid Proof Format'"]
        ValidateProofJSON -- "Yes" --> SubmitZKP["POST /api/v2/zkp/credential-predicates/verify"]
        SubmitZKP --> ZKPOutcome{"Bulletproofs Cryptographic Verification"}
        ZKPOutcome -- "Valid (HTTP 200)" --> ZKPSuccess["Display 'Predicate Valid: Claim Proven Without Disclosure'"]
        ZKPOutcome -- "Invalid Proof" --> ZKPFail["Display 'Verification Failed: Proof is mathematically invalid'"]
    end

    ExportProof -. Handover Proof Payload .-> PasteProof
```

### G.3 Step-by-Step Specification

| Step | User Action | Interface Response | Backend Operation | Technical Details |
| :---: | :--- | :--- | :--- | :--- |
| **G.1** | Holder opens `PredicateProofGeneratorCard` on `/holder`. | Displays available numerical claims (e.g. `birthDate`, `annualIncome`) and operator dropdown (`>=`, `<=`, `between`). | None. | Claim values extracted from held credentials. |
| **G.2** | Selects `Age >= 21` and clicks `"Generate Proof"`. | Spinner activates: *"Computing Bulletproofs Commitment & Range Proof..."*. | `POST /api/holder/credentials/proofs/generate` | Backend Weavechain Bulletproofs library constructs zero-knowledge proof over Pedersen commitment. |
| **G.3** | Holder copies proof payload. | Output card renders formatted JSON payload with one-click copy and QR matrix generation. | None. | Output contains Pedersen commitment and proof vector; does NOT contain birthdate. |
| **G.4** | Verifier visits `/predicate-verification` and pastes proof JSON. | Input validator verifies structure; activates `"Verify Predicate Proof"` button. | None. | Public verification tab. |
| **G.5** | Verifier clicks `"Verify Predicate Proof"`. | Displays verification progress; returns **ZKP Verification Badge** in cyan/green: *"Predicate Proven: Condition 'Age >= 21' is mathematically true. The underlying age was not disclosed to this platform."* | `POST /api/v2/zkp/credential-predicates/verify` | Backend Bulletproofs verifier verifies proof against commitment. |

---

## 3. Summary of Journey Coverage & Verification Modalities

| Journey Identifier | Name & Role | Primary Endpoint(s) | Verification Modality | Privacy Disclosure Profile |
| :--- | :--- | :--- | :--- | :--- |
| **Journey A** | Document Registration (`ISSUER`) | `POST /api/documents` | N/A (Ingestion & Signing) | Uploads document to backend storage; generates public QR reference. |
| **Journey B** | QR-Code Verification (Public) | `GET /api/public/verify/capability/{token}` | **QR / Capability Token** | Discloses authenticity, issuer, and date. Plaintext document remains **hidden**. |
| **Journey C** | Direct Document Verification (Public) | `POST /api/public/verify/upload` | **Direct File Upload** | Completely independent of QR code. Verifies file hash statelessly. |
| **Journey D** | Operator Authentication (All Roles) | RFC 8725 JWT Client Verification | Session Management | Persists in `sessionStorage`. Validates RS256/PS256 signatures. |
| **Journey E** | Holder Credential Wallet (`HOLDER`) | `GET /api/holder/credentials` | Identity Management | Full claim inspection for holder; self-service suspension controls. |
| **Journey F** | Platform Trust Admin (`ADMIN`) | `POST /api/admin/keys`, `/verify-integrity` | Governance & Audit | Manages public keys; verifies Merkle hash chain across audit events. |
| **Journey G** | ZKP Predicate Proof (Public/Holder) | `POST /api/v2/zkp/credential-predicates/verify`| **Zero-Knowledge Proof** | Proves numerical condition without disclosing underlying attribute value. |
