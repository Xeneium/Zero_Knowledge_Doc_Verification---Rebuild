# 🎤 ZKDoc Platform — Live Demonstration & Defense Script

**Target Duration:** 8 to 12 minutes  
**Target Audience:** Project Review Committee, Academic Evaluators, Security Engineers, and Stakeholders  
**Presenter Goal:** Confidently demonstrate end-to-end cryptographic document verification, real zero-knowledge range proofs, tamper detection, and administrator audit chains using the actual running application.

---

## ⏱️ Timeline & Stage Overview

| Stage | Duration | Persona / Role | Core Technical Focus |
| :--- | :--- | :--- | :--- |
| **Stage 1** | 0:00 – 1:30 | *Presenter* | Motivation, privacy problem in credential verification, ZKP solution |
| **Stage 2** | 1:30 – 3:30 | **ISSUER** | Document intake, SHA-256 hashing, RSASSA-PSS digital signature, credential issuance |
| **Stage 3** | 3:30 – 6:00 | **HOLDER** (Alice) | Alice's wallet, Pedersen commitment, generating Bulletproof range proof (`GPA >= 3.50`) |
| **Stage 4** | 6:00 – 8:00 | **VERIFIER** | Verifying ZKP proof (zero disclosure of true GPA), QR capability verification |
| **Stage 5** | 8:00 – 9:30 | **ADVERSARIAL** | Uploading tampered document (`demo-degree-diploma-tampered.pdf`) & tamper detection |
| **Stage 6** | 9:30 – 11:00 | **ADMIN** | Key registry, system health, and SHA-256 tamper-evident audit hash-chain verification |
| **Stage 7** | 11:00 – 12:00 | *Presenter* | Concluding summary, architectural guarantees, opening for viva/questions |

---

## 🎬 Step-by-Step Presentation Script

---

### Stage 1: The Privacy Dilemma in Document Verification (0:00 – 1:30)

#### 🖥️ Screen Action:
- Open browser to **`http://localhost:8080/`** (Overview Page).

#### 🗣️ Spoken Script:
> *"Good morning, esteemed committee and reviewers. Today, I am demonstrating the **Zero-Knowledge Document Verification Platform**, or ZKDoc.*
>
> *In traditional digital verification—such as applying for a job, mortgage, or scholarship—an applicant is forced to surrender entire PDF documents containing their name, address, full grade transcripts, or dates of birth. The verifying party only needs to know one simple fact, for example: 'Is this candidate's GPA at least 3.5?' or 'Are they over 18?' Yet they are handed every private detail.*
>
> *ZKDoc eliminates this unnecessary disclosure. Built on **Java 21**, **Spring Boot**, and **PostgreSQL**, with cryptographic foundations in **SHA-256**, **RSASSA-PSS**, and **Bulletproof range proofs**, our platform enables holders to prove mathematical statements about their credentials without disclosing the underlying data."*

---

### Stage 2: Document Ingestion, Signing & Credential Issuance (1:30 – 3:30)

#### 🖥️ Screen Action:
1. Click **"Connect Session"** or navigate to **`/login`**.
2. Under **"Evaluation Demo Accounts"**, click the **`ISSUER` (`issuer-agent`)** card.
3. Observe the live Decoded Token Claims Preview (Subject: `urn:zkdoc:issuer:acme-university`, Role: `ROLE_ISSUER`).
4. Click **"Connect Session"**.
5. Navigate to **"Document Studio"** (`/documents`).
6. Point to the pre-seeded document `demo-degree-diploma.pdf`.
7. Click **"View Details"** or upload a new file.

#### 🗣️ Spoken Script:
> *"I have logged in as **Acme University**, our certified credential issuer. Notice that in ZKDoc, authentication uses stateless Bearer JWTs signed with RSA-2048, adhering strictly to RFC 7519 and RFC 8725 standards. Tokens are stored purely in client memory, preventing XSS-based storage leaks.*
>
> *When a document like Alice's diploma is ingested, the system calculates its streaming SHA-256 digest on the fly. Acme University signs this digest using **RSASSA-PSS** with MGF1 and a 32-byte salt—the cryptographically modern successor to legacy PKCS#1 v1.5.*
>
> *Furthermore, when issuing Alice's credential, rather than storing her numeric GPA as plaintext, the system generates a **Pedersen Commitment**: $C = v \cdot G + r \cdot H$. The commitment is public, but her true GPA of 3.80 is mathematically hidden by the random scalar $r$."*

---

### Stage 3: Holder Wallet & Zero-Knowledge Proof Generation (3:30 – 6:00)

#### 🖥️ Screen Action:
1. Click the profile icon in the top right, select **"Disconnect Session"**.
2. On `/login`, click **`HOLDER` (`alice-chen`)**. Click **"Connect Session"**.
3. The browser redirects to **`/credentials`** (Alice's Credential Wallet).
4. Alice's pre-seeded Bachelor of Science credential appears in the list.
5. Click **"Zero-Knowledge Prover"** / **"Generate Proof"**.
6. Set the attribute to **`gpa`**, operation to **`GREATER_THAN_OR_EQUAL`**, and threshold to **`3.50`** (internal integer `350`).
7. Click **"Generate Bulletproof Range Proof"**.
8. Inspect the resulting cryptographic proof envelope (Proof Bytes, Pedersen Commitment Anchor, Threshold).

#### 🗣️ Spoken Script:
> *"Now we switch to Alice's perspective. In her identity wallet, Alice sees her Bachelor of Science credential. Notice that her public attributes—like degree name and graduation year—are visible, but her private GPA is protected.*
>
> *Suppose a prospective employer asks: 'Do you meet our 3.50 GPA cutoff?' Alice does not want to send her entire transcript. Instead, using her locally stored secret witness in her proving material store, she constructs a **Bulletproof range proof**.*
>
> *The Bulletproof proves that $(380 - 350) \ge 0$, or that the difference falls strictly within the 64-bit integer range $[0, 2^{64}-1]$. This computation requires zero trusted setup ceremony, making it resilient and transparent."*

---

### Stage 4: Verifier Workflow & Zero-Knowledge Verification (6:00 – 8:00)

#### 🖥️ Screen Action:
1. Disconnect Alice's session.
2. Navigate to **`/verify`** (Public Verification Portal).
3. Select the **"Capability & ZKP Verification"** tab.
4. Paste Alice's pre-seeded capability token `tok_demo_alice_degree_cred` or the proof payload.
5. Click **"Verify Cryptographic Statement"**.
6. Observe the verification result:
   - Status: **`VERIFIED`** (Emerald badge)
   - Predicate: `gpa >= 3.50`
   - Issuer: `urn:zkdoc:issuer:acme-university`
   - Credential Status: `ACTIVE`

#### 🗣️ Spoken Script:
> *"Now let us observe the verifier's experience. This page is publicly accessible without prior registration.*
>
> *When the verifier evaluates Alice's proof against Acme University's public key and commitment anchor, the backend executes the Bulletproof verification algorithm. The result confirms with 100% mathematical certainty that Alice's GPA is at least 3.50.*
>
> *Crucially, observe the information boundary: the verifier learns that the predicate is TRUE, but they have learned **zero bits of information** about whether Alice scored 3.50, 3.80, or 4.00. Privacy is completely preserved."*

---

### Stage 5: Adversarial Demonstration — Tamper Detection (8:00 – 9:30)

#### 🖥️ Screen Action:
1. On the **`/verify`** page, click the **"Upload Document (Mode A)"** tab.
2. Log in as **`VERIFIER` (`verifier-auditor`)** via `/login` to access Mode A artifact verification.
3. Drag and drop the valid document: `demo/fixtures/demo-degree-diploma.pdf`.
4. Click **"Verify Document"** $\rightarrow$ Observe **`VALID / AUTHENTIC`** with SHA-256 match `cc5e04...`.
5. Now, drag and drop the tampered document: `demo/fixtures/demo-degree-diploma-tampered.pdf`.
6. Click **"Verify Document"** $\rightarrow$ Observe **`INTEGRITY VIOLATION / VERIFICATION FAILED`** alert!

#### 🗣️ Spoken Script:
> *"Now let us test the platform under adversarial conditions.*
>
> *In `demo-degree-diploma-tampered.pdf`, an attacker altered two text fields: they upgraded their honors from Magna Cum Laude to Summa Cum Laude, and changed their GPA from 3.80 to 4.00.*
>
> *When we upload this altered PDF, the streaming SHA-256 hasher calculates digest `5bc2b6...`. The platform looks up the registered record and immediately detects that this digest does not match the issuer's signed anchor `cc5e04...`.*
>
> *The verification fails instantly with an **Integrity Violation** warning, and the failed attempt is logged with an immutable audit entry."*

---

### Stage 6: Administrator Oversight & Audit Hash Chain (9:30 – 11:00)

#### 🖥️ Screen Action:
1. Log in as **`ADMIN` (`admin-operator`)**.
2. Navigate to **`/admin`** (Administrator Console).
3. View the **Key Registry** (inspect active RSA-PSS keys).
4. Scroll to the **Tamper-Evident Audit Logs** table.
5. Click **"Verify Audit Hash Chain Integrity"**.
6. Observe the result: **`Hash Chain Intact — 100% Verified`**.

#### 🗣️ Spoken Script:
> *"Finally, we view the system through the eyes of the **System Administrator**.*
>
> *Here, administrators manage cryptographic trust anchors, monitor rate limiting via Bucket4j token buckets, and inspect operational telemetry.*
>
> *Most importantly, ZKDoc maintains a tamper-evident audit log structured as a cryptographic hash chain. Each log entry is bound to the SHA-256 digest of the preceding row. If a malicious database administrator were to alter or delete any audit entry, clicking 'Verify Audit Hash Chain' would immediately flag the exact corrupted block.*
>
> *As you can see, our full chain reconciles with zero discrepancies."*

---

### Stage 7: Conclusion & Defense Readiness (11:00 – 12:00)

#### 🗣️ Spoken Script:
> *"To summarize:*
> - *We demonstrated **RSASSA-PSS digital signatures** and **SHA-256 integrity** for document authenticity.*
> - *We proved **zero-knowledge selective disclosure** using real **Pedersen commitments** and **Bulletproof range proofs** over 64-bit integers.*
> - *We demonstrated **immediate tamper detection** against altered documents.*
> - *We validated enterprise-grade **RBAC**, **token-bucket rate limiting**, and **tamper-evident audit chaining**.*
>
> *The platform runs on clean Java 21 LTS with modular Maven architecture and zero mock cryptography. Thank you, and I look forward to your questions."*

---

## 🛠️ Emergency Recovery Playbook

If any glitch occurs during your live demonstration, stay calm and apply these instant recovery steps:

| Incident | Immediate Recovery Action |
| :--- | :--- |
| **Accidental Logout or Lost Token** | Click "Return to Login", click the corresponding role button in the **Demo Quick Connect** card, and click **"Connect Session"**. Takes under 3 seconds. |
| **Browser Camera Permission Denied for QR Scan** | Do not fumble with browser settings. Click the **"File Upload"** or **"Manual Reference"** tab and use the pre-seeded capability token `tok_demo_alice_degree_cred`. |
| **Port 8080 In Use Before Demo** | Run `Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess | Stop-Process -Force` in PowerShell, then re-run `.\scripts\demo-setup.ps1`. |
| **Tampered File Not Found** | Open `demo/fixtures/` in Windows File Explorer and drag `demo-degree-diploma-tampered.pdf` directly into the browser dropzone. |
| **Evaluator Asks: *"Can you verify arbitrary circuits or equality proofs?"*** | Candidly explain: *"ZKDoc is engineered specifically for predicate range proofs ($x \ge \text{threshold}$) using Bulletproofs, which eliminates the need for a trusted setup. It is purposefully optimized for numeric credential verification rather than arbitrary general-purpose circuits."* |
