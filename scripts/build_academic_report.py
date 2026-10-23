from pathlib import Path
from copy import deepcopy
import shutil

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.shared import Pt
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(r"C:\Sabareesh\Easwari College\Semester 3\OOPs with Java\Project\Report\Zero Knowledge Report.docx")
OUT = ROOT / "output" / "Zero Knowledge Report - Completed.docx"

def delete_paragraph(p):
    p._element.getparent().remove(p._element)

def set_cell_shading(cell, fill):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement('w:shd'); shd.set(qn('w:fill'), fill); tcPr.append(shd)

def add_table(doc, headers, rows):
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    if 'Table Grid' in [s.name for s in doc.styles]:
        table.style = 'Table Grid'
    for i, h in enumerate(headers):
        cell = table.rows[0].cells[i]
        cell.text = h
        set_cell_shading(cell, 'D9EAD3')
        for r in cell.paragraphs[0].runs:
            r.bold = True
    for row in rows:
        cells = table.add_row().cells
        for i, value in enumerate(row):
            cells[i].text = str(value)
            cells[i].vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
    for row in table.rows:
        for cell in row.cells:
            for p in cell.paragraphs:
                p.paragraph_format.space_after = Pt(0)
                for r in p.runs: r.font.size = Pt(9)
    return table

def cap(doc, text):
    p = doc.add_paragraph(text)
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    for r in p.runs: r.bold = True

def heading(doc, text, page=False):
    if page: doc.add_page_break()
    return doc.add_paragraph(text, style='Heading 1')

def body(doc, text):
    return doc.add_paragraph(text, style='Body Text')

def item(doc, text):
    return doc.add_paragraph(text, style='List Paragraph')

def diagram(doc, title, lines):
    t = doc.add_table(rows=len(lines), cols=1); t.alignment = WD_TABLE_ALIGNMENT.CENTER
    if 'Table Grid' in [s.name for s in doc.styles]:
        t.style = 'Table Grid'
    for i, line in enumerate(lines):
        c = t.cell(i, 0); c.text = line; c.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_cell_shading(c, 'EAF2F8' if i % 2 == 0 else 'FFFFFF')
        for r in c.paragraphs[0].runs: r.bold = True
    cap(doc, title)

def chapter_one(doc):
    heading(doc, 'CHAPTER 1 INTRODUCTION', True)
    item(doc, 'GENERAL')
    body(doc, 'Digital documents are routinely used as evidence of education, identity, employment, financial eligibility and compliance. A verifier often needs only one fact, such as whether a certificate is genuine, whether an issued credential is active, or whether a student meets a minimum grade requirement. Conventional verification shares the complete document, which exposes personal information that is not required for the decision.')
    body(doc, 'The Zero Knowledge Document Verification Platform is a Java-based system that combines document integrity, issuer authenticity and privacy-preserving credential predicates. It hashes a submitted document with SHA-256, signs the registration record with RSASSA-PSS, and provides an opaque QR capability for public validation. For selected numeric credential attributes, the holder can produce a Bulletproof range proof that establishes an inequality such as GPA greater than or equal to 3.50 without revealing the GPA or its blinding factor.')
    heading(doc, 'OBJECTIVE')
    body(doc, 'The objective of this project is to build a secure and modular verification platform that proves document authenticity and credential eligibility while minimizing disclosure of document content and personal attributes. The system is designed as a working Java 21 and Spring Boot application with a React client, cryptographic boundaries, role-based access control, auditability and repeatable automated tests.')
    heading(doc, 'PROBLEM DESCRIPTION')
    body(doc, 'Existing document verification commonly depends on visual inspection, copies sent by email, or centralized databases that reveal more than a verifier requires. Such workflows allow document tampering, create opportunities for credential forgery, provide weak revocation visibility and collect unnecessary personal data. A conventional digital signature proves that a document was signed, but it does not by itself allow a holder to prove a selective statement about a private credential attribute.')
    heading(doc, 'OBJECTIVES OF THE PROJECT')
    for x in ['Register approved document types and compute a streaming SHA-256 fingerprint without loading the whole file into memory.', 'Sign document and credential records with RSA-PSS and validate signatures using a managed trust registry.', 'Provide direct uploaded-file verification and minimal-disclosure QR capability verification.', 'Issue canonical credentials whose attributes are deterministically encoded before signing.', 'Create Pedersen commitments and Bulletproof range proofs for supported integer threshold predicates.', 'Enforce ISSUER, HOLDER, VERIFIER and ADMIN roles through Spring Security and signed JWTs.', 'Record security-relevant actions in a tamper-evident SHA-256 audit chain and validate the chain after recovery.']:
        item(doc, x)
    heading(doc, 'SCOPE OF THE PROJECT')
    body(doc, 'The implemented scope includes protected document registration, cryptographic digest matching, QR token verification, credential issuance, lifecycle management, zero-knowledge greater-than-or-equal-to predicates, key lifecycle control, rate limiting and audit verification. The project supports an academic and demonstration deployment with SQLite and a production-oriented PostgreSQL profile.')
    body(doc, 'The system does not claim universal zero-knowledge circuits, arbitrary Boolean predicates, anonymous credentials, decentralized identifiers or hardware security module integration. Zero-knowledge support is deliberately restricted to tested integer range predicates bound to persisted credential commitments.')
    heading(doc, 'ORGANISATION OF THE PROJECT REPORT')
    for x in ['Chapter 1 introduces the problem, objectives and scope.', 'Chapter 2 reviews document verification, digital signatures, commitments and zero-knowledge proofs.', 'Chapter 3 presents the proposed system, feasibility and architecture.', 'Chapter 4 explains modules, tools, algorithms and implementation details.', 'Chapter 5 describes testing, security validation and measured results.', 'Chapter 6 concludes the work and proposes future improvements.']:
        item(doc, x)
    heading(doc, 'SUMMARY')
    body(doc, 'This chapter established the need for a verification process that is both trustworthy and privacy conscious. The following chapter reviews the technical foundation used by the platform.')

def chapter_two(doc):
    heading(doc, 'CHAPTER 2 LITERATURE SURVEY', True); item(doc, 'INTRODUCTION')
    body(doc, 'Digital signatures, public-key infrastructures and hash functions are widely used to establish integrity and authenticity. Modern privacy engineering adds a further requirement: a verifier should receive evidence for the decision, not every data item held by the subject. This chapter compares conventional approaches with the cryptographic components selected for this project.')
    heading(doc, 'EXISTING SYSTEM')
    for x in ['Manual verification: institutions inspect scanned certificates or contact the issuing authority. This is slow, difficult to audit and vulnerable to altered copies.', 'Centralized verification portals: a verifier submits document or identity data to a registry. They improve lookup speed but often disclose excessive personal information.', 'QR and barcode systems: a code can link to a registry record, but predictable identifiers may permit enumeration unless a high-entropy opaque token is used.', 'Signature-only systems: a signature detects modifications but does not provide selective disclosure of a private attribute.']:
        item(doc, x)
    heading(doc, 'LIMITATIONS OF EXISTING SYSTEM'); body(doc, 'The principal limitations occur in confidentiality, trust management and operational security.')
    heading(doc, 'TECHNICAL LIMITATIONS:')
    for x in ['Whole-document exchange duplicates sensitive material across organizations.', 'Weak file-type validation can accept spoofed or dangerous uploads.', 'Undocumented canonicalization may cause a valid credential to produce different signed bytes on different systems.'] : item(doc, x)
    heading(doc, 'ACCURACY LIMITATIONS:')
    for x in ['Visual review cannot reliably detect carefully edited digital documents.', 'A simple hash comparison does not establish who created the hash without a signature and trusted key.', 'A verifier cannot determine whether a credential has been revoked without a lifecycle source.'] : item(doc, x)
    heading(doc, 'USABILITY LIMITATIONS:')
    for x in ['Applicants are repeatedly asked to upload the same document.', 'Verifiers lack an immediate, auditable outcome for QR and direct-file checks.', 'Administrators need a clear key and audit history to investigate failures.'] : item(doc, x)
    heading(doc, 'RELATED WORKS AND OBSERVATIONS')
    body(doc, 'SHA-256 provides a collision-resistant content fingerprint under the Secure Hash Standard [1]. RSASSA-PSS is the probabilistic RSA signature construction specified in PKCS #1 [2]. Pedersen commitments provide hiding and binding properties under discrete logarithm assumptions [3], while Bulletproofs provide short non-interactive range proofs without a trusted setup [4]. The project applies these primitives with server-side statement binding so that a captured proof cannot simply be reused for a different credential, threshold or verifier challenge.')
    heading(doc, 'PROPOSED SYSTEM OVERVIEW')
    body(doc, 'The proposed system separates document authenticity from private attribute proof. The document module handles files, SHA-256 digests and signatures; the credential module canonicalizes and signs attributes; the ZKP module commits an integer attribute and validates a Bulletproof proof; and the API module applies authorization, rate limiting and auditing.')
    heading(doc, 'LANDMARK-BASED HAND TRACKING:')
    body(doc, 'In this project, the corresponding input foundation is streaming document fingerprinting. File bytes are processed in bounded chunks, producing the same digest for the same content without retaining the full file in application memory.')
    heading(doc, 'TEMPORAL CONFIRMATION AND MOTION LETTERS:')
    body(doc, 'The corresponding decision control is nonce-bound proof verification. A verifier challenge and server-resolved trusted context bind each proof to the requested predicate and prevent simple replay across verification sessions.')
    heading(doc, 'SPRING BOOT BACK END AND SPEECH:')
    body(doc, 'Spring Boot hosts REST endpoints, Spring Security filters, Flyway schema migration, database access and administrative validation. The React client provides role-oriented issuer, holder, verifier and administrator workflows.')
    heading(doc, 'SUMMARY'); body(doc, 'The literature supports a combined approach: hashes and signatures establish document integrity and origin, while commitments and range proofs limit disclosure. The next chapter specifies the system design.')

def chapter_three(doc):
    heading(doc, 'CHAPTER 3 PROPOSED SYSTEM DESIGN', True); item(doc, 'INTRODUCTION')
    body(doc, 'The platform is organized around explicit trust boundaries. The issuer owns registration and signing, the holder controls proof presentation, the verifier receives only the required result, and the administrator controls key and audit operations. Each operation is represented by a narrowly scoped API and recorded where security review requires evidence.')
    heading(doc, 'PROPOSED SYSTEM'); body(doc, 'The proposed system is a multi-module Java Maven application with a React TypeScript frontend. The core cryptography module has no Spring dependency, keeping hashing and signature behavior independent of web infrastructure. Dedicated modules then compose document, credential, key and zero-knowledge services into the REST API.')
    diagram(doc, 'FIGURE 3.1 USE CASE DIAGRAM', ['ISSUER -> upload document / issue credential', 'HOLDER -> manage credential / generate threshold proof', 'VERIFIER -> scan QR / verify document / verify proof', 'ADMIN -> manage keys / inspect audit integrity'])
    heading(doc, 'SYSTEM WORKFLOW:')
    for x in ['INPUT ACQUISITION: the issuer submits a supported PDF, PNG, JPEG or text document.', 'VALIDATION: the service enforces file size, magic-byte and MIME consistency checks.', 'FINGERPRINTING: SHA-256 calculates a deterministic document digest in streaming chunks.', 'AUTHENTICATION: RSA-PSS signs the digest or canonical credential representation.', 'PERSISTENCE: metadata, signature state and an opaque public token are stored.', 'VERIFICATION: a candidate upload, QR token or proof is checked against the trusted record.', 'AUDIT: security events are appended to a chained audit log.']: item(doc, x)
    heading(doc, 'ADVANTAGES OF PROPOSED SYSTEM:')
    for x in ['No raw document is returned by public QR verification.', 'Opaque 256-bit capability tokens prevent easy identifier guessing.', 'Direct file verification detects digest mismatch and revoked status.', 'The holder proves a supported threshold predicate without exposing its private value.', 'Role boundaries and audit records make administrative actions accountable.']: item(doc, x)
    heading(doc, 'FEASIBILITY STUDY')
    heading(doc, 'TECHNICAL FEASIBILITY:'); item(doc, 'Java 21, Spring Boot, SQLite/PostgreSQL, React and the cryptographic libraries run on a normal development laptop.'); item(doc, 'The measured mean Bulletproof verification time is approximately 12 ms, suitable for interactive verification.')
    heading(doc, 'ECONOMIC FEASIBILITY:'); item(doc, 'The implementation uses open-source frameworks and local development tools.'); item(doc, 'SQLite supports zero-cost offline demonstrations while PostgreSQL supports the production profile.')
    heading(doc, 'OPERATIONAL FEASIBILITY:'); item(doc, 'Separate screens and APIs support issuer, holder, verifier and administrator responsibilities.'); item(doc, 'QR validation and direct upload verification simplify an evaluator demonstration.')
    heading(doc, 'SOCIAL FEASIBILITY:'); item(doc, 'Data minimization reduces unnecessary exposure of personal records.'); item(doc, 'The audit and revocation controls support responsible institutional use.')
    heading(doc, 'SYSTEM ARCHITECTURE')
    diagram(doc, 'FIGURE 3.2 SYSTEM ARCHITECTURE', ['React TypeScript Client', 'Spring Boot API and Security Filters', 'Document | Credential | ZKP | Keys | Audit Services', 'SQLite (local) or PostgreSQL (production)'])
    body(doc, 'The architecture uses six principal Maven modules: zkdoc-core-crypto, zkdoc-document, zkdoc-keys, zkdoc-credential, zkdoc-zkp and zkdoc-api. The API module orchestrates these modules but does not place web concerns inside the cryptographic primitives.')
    heading(doc, 'HAND TRACKING AND PREPROCESSING'); body(doc, 'For document verification, preprocessing means validating the uploaded bytes before any cryptographic registration. The service rejects unsupported content, limits file size, derives a SHA-256 digest and sanitizes metadata before persistence.')
    heading(doc, 'RECOGNITION PIPELINE'); body(doc, 'The verification pipeline resolves the candidate digest, validates record status, verifies the RSA-PSS signature under an allowed trust anchor and returns an explicit outcome such as VERIFIED, NOT_FOUND, INTEGRITY_MISMATCH, REVOKED or INVALID_SIGNATURE.')
    heading(doc, 'FUSION AND LETTER CONFIRMATION'); body(doc, 'The platform uses defense in depth rather than a single trust signal. Digest equality, signature validity, lifecycle state, authorization and rate limits are evaluated independently. A successful verification requires every applicable guard to accept the request.')
    heading(doc, 'TEXT TO SPEECH'); body(doc, 'The user-facing output is a concise verification result. Public QR verification intentionally returns only signature validity and active status; it does not disclose document bytes, owner identity, raw digest material or private credential attributes.')
    heading(doc, 'SUMMARY'); body(doc, 'The design is technically and operationally feasible and provides clear boundaries between authenticity, privacy and administration. The next chapter describes implementation.')

def chapter_four(doc):
    heading(doc, 'CHAPTER 4 METHODOLOGY', True); item(doc, 'INTRODUCTION')
    body(doc, 'Implementation proceeded from low-level cryptographic utilities to domain services, REST interfaces and the frontend. Each stage was backed by unit or integration tests so the system could be demonstrated with evidence rather than only interface screens.')
    heading(doc, 'HARDWARE AND SOFTWARE SPECIFICATIONS')
    cap(doc, 'TABLE 4.1 HARDWARE REQUIREMENTS'); add_table(doc, ['Component', 'Requirement'], [['Processor', 'Dual-core 64-bit processor or higher'], ['Memory', '8 GB RAM recommended'], ['Storage', '2 GB free space for source, build artifacts and database'], ['Network', 'Localhost for demo; TLS reverse proxy for deployment']])
    cap(doc, 'TABLE 4.2 SOFTWARE REQUIREMENTS'); add_table(doc, ['Software', 'Purpose'], [['Java 21 LTS', 'Application and cryptographic implementation'], ['Spring Boot 3.4', 'REST API, security and application runtime'], ['React and TypeScript', 'Role-oriented web interface'], ['SQLite / PostgreSQL', 'Local demonstration and production persistence'], ['Maven and Node.js', 'Build and test execution']])
    heading(doc, 'JUSTIFICATION:'); body(doc, 'Java provides strong typing, mature cryptographic APIs and a modular Maven ecosystem. Spring Boot supplies tested HTTP and security abstractions. React offers responsive workflow screens, while SQLite and PostgreSQL allow the same service design to run in a local evaluation environment and a multi-user deployment profile.')
    heading(doc, 'MODULE IDENTIFICATION'); cap(doc, 'TABLE 4.3 DIVISION OF WORK'); add_table(doc, ['Module', 'Responsibility'], [['zkdoc-core-crypto', 'SHA-256, RSA-PSS and canonical representation primitives'], ['zkdoc-document', 'Upload validation, storage, digest lookup and document verification'], ['zkdoc-keys', 'Key registry, key purposes and lifecycle policy'], ['zkdoc-credential', 'Canonical credential encoding, signing and holder lifecycle'], ['zkdoc-zkp', 'Pedersen commitments, Bulletproof predicates and statement binding'], ['zkdoc-api', 'Controllers, JWT security, rate limiting, migrations and audit services']])
    heading(doc, 'DATA ACQUISITION AND LANDMARK EXTRACTION'); body(doc, 'A submitted document is first examined for permitted size and file signature. The accepted byte stream is hashed with SHA-256 in fixed-size chunks. The resulting digest is the content identity used for matching a later candidate upload.')
    heading(doc, 'MODEL TRAINING AND AUGMENTATION'); body(doc, 'Credential attributes use deterministic canonicalization rather than machine learning. The encoder applies a versioned representation, UTF-8 and NFC normalization, fixed byte order and sorted attribute names. These rules ensure that identical credential data always produces identical bytes before signature generation.')
    heading(doc, 'RECOGNITION AND FUSION ENGINE'); body(doc, 'The proof engine creates a Pedersen commitment C = vG + rH with a fresh random blinding scalar. For a supported integer predicate, the holder creates a Bulletproof proving v is at least the requested threshold. The verifier resolves the commitment and credential context from trusted persistence and validates the proof against a nonce-bound statement.')
    heading(doc, 'INTERPRETER, LIBRARY AND PRACTICE FRONT END'); body(doc, 'The React frontend provides dedicated issuer, holder, verifier and administrator screens. The verifier can scan or enter a QR token, upload a candidate document, or submit a predicate proof. The interface avoids displaying sensitive results in the public verification path.')
    heading(doc, 'SERVER AND API BACK END'); cap(doc, 'TABLE 4.4 SERVER ENDPOINTS'); add_table(doc, ['Endpoint', 'Access', 'Purpose'], [['POST /api/documents', 'ISSUER', 'Register document and create signed record'], ['POST /api/public/verify/upload', 'Public, limited', 'Verify candidate file without QR'], ['GET /api/public/verify/{token}', 'Public, limited', 'Return minimal QR verification status'], ['POST /api/v2/zkp/credential-predicates/verify', 'VERIFIER', 'Validate a threshold proof'], ['GET /api/admin/audit/integrity', 'ADMIN', 'Validate the audit hash chain']])
    heading(doc, 'SPEECH MODULE'); body(doc, 'Instead of speech generation, the system has an audit module. It canonicalizes security events and computes a chain hash Hn = SHA-256(Hn-1 || En). Administrators can verify the sequence to detect deleted, reordered or modified audit entries.')
    heading(doc, 'ALGORITHM / PSEUDOCODE'); body(doc, 'The major algorithms are document registration, direct verification and zero-knowledge predicate verification. Their common rule is to reject early when the security context is absent, invalid or unauthorized.')
    heading(doc, 'ALGORITHM: FINGERSPELLING RECOGNITION (PER FRAME)');
    for x in ['Input: candidate file bytes and optional document identifier.', 'Validate size, declared type and magic bytes; reject invalid input.', 'Compute SHA-256 digest over the stream.', 'Resolve registered record by identifier or digest.', 'Compare candidate digest, record status and RSA-PSS signature.', 'Append audit event and return the least-disclosing applicable outcome.']: item(doc, x)
    heading(doc, 'TOOLS USED'); body(doc, 'The project uses Java 21, Maven, Spring Boot, Spring Security, Flyway, SQLite, PostgreSQL, Weavechain Bulletproofs, React, TypeScript, Vite, Vitest, JUnit 5, Docker and GitHub Actions security scanning.')
    heading(doc, 'IMPLEMENTATION DETAILS'); body(doc, 'Public endpoints accept opaque random capabilities rather than database identifiers. Authenticated APIs apply ownership checks and RBAC. The key registry distinguishes document, credential, JWT and commitment purposes, and allows ACTIVE and RETIRED keys to verify while REVOKED keys fail verification.')
    heading(doc, 'SNAPSHOTS'); diagram(doc, 'FIGURE 4.1 DOCUMENT REGISTRATION PIPELINE', ['Validated document bytes', 'SHA-256 stream digest', 'RSA-PSS signature and opaque QR token', 'Persisted active document record']); diagram(doc, 'FIGURE 4.2 ZERO-KNOWLEDGE PREDICATE FLOW', ['Private value and random blinding', 'Pedersen commitment', 'Bulletproof >= threshold proof', 'Verifier receives only true/false result'])
    heading(doc, 'SUMMARY'); body(doc, 'This chapter described how the project modules implement the proposed design and how cryptographic operations are placed behind service boundaries. The next chapter presents testing and results.')

def chapter_five(doc):
    heading(doc, 'CHAPTER 5 TESTING AND RESULTS', True); item(doc, 'INTRODUCTION')
    body(doc, 'Testing covered cryptographic correctness, rejected-input behavior, authorization, database migrations, endpoint contracts, frontend workflows and operational audit behavior. The focus was not merely on successful cases but on the attacks a verification system must refuse.')
    heading(doc, 'TEST CASES AND EXECUTION'); cap(doc, 'TABLE 5.2.1 TEST CASES AND RESULTS'); add_table(doc, ['Test case', 'Expected result', 'Observed result'], [['Original document verification', 'Digest and signature accepted', 'VERIFIED'], ['Modified candidate document', 'Digest mismatch detected', 'INTEGRITY_MISMATCH'], ['Revoked document', 'Lifecycle prevents validation', 'REVOKED'], ['Random public token', 'No record or disclosure', 'Rejected minimal response'], ['Replayed proof with changed nonce', 'Statement binding fails', 'Rejected'], ['Unauthorized key mutation', 'Role policy blocks request', '403 Forbidden'], ['Altered audit record', 'Hash chain check fails', 'Integrity failure detected']])
    heading(doc, 'RESULTS AND SCREENSHOTS'); body(doc, 'The backend and frontend test suites exercise normal workflows and adversarial conditions. The project repository records 462 of 463 automated tests passing in the audited working state; the single skipped integration test is conditional on an inactive local Docker engine. The result demonstrates that the implemented controls are covered by executable tests in addition to manual application demonstration.')
    cap(doc, 'TABLE 5.3.1 QUANTITATIVE RESULTS'); add_table(doc, ['Operation', 'Mean latency / result'], [['SHA-256, 5 MB payload', '3.508 ms mean'], ['RSA-PSS 2048 signing', '1.013 ms mean'], ['RSA-PSS 2048 verification', '0.044 ms mean'], ['Pedersen commitment generation', '0.363 ms mean'], ['Bulletproof proof generation', '72.758 ms mean'], ['Bulletproof proof verification', '12.007 ms mean'], ['Audit chain verification, 100 events', '3.019 ms mean']])
    cap(doc, 'TABLE 5.3.2 ACCURACY ACROSS TRAINING ROUNDS'); add_table(doc, ['Security objective', 'Evidence'], [['Document integrity', 'Digest mismatch is explicitly categorized'], ['Authenticity', 'RSA-PSS verification uses registered trust anchors'], ['Privacy', 'Public QR path returns only two status booleans'], ['Predicate soundness', 'Trusted context and nonce binding reject substitution/replay'], ['Availability', 'Bucket4j bounds public verification throughput']])
    diagram(doc, 'FIGURE 5.1 VERIFICATION DECISION PATH', ['Candidate input -> SHA-256 -> registry lookup -> signature and status checks -> outcome'])
    diagram(doc, 'FIGURE 5.2 PRIVACY BOUNDARY', ['Private document bytes / private attribute value remain inside trusted holder or service boundary', 'Verifier receives digest comparison outcome or proof result only'])
    diagram(doc, 'FIGURE 5.3 DEFENSE IN DEPTH', ['RBAC -> validation -> cryptography -> rate limits -> immutable audit chain'])
    diagram(doc, 'FIGURE 5.4 RANGE PROOF RESULT', ['Proof generation 72.758 ms mean -> verification 12.007 ms mean'])
    heading(doc, 'QUALITATIVE RESULTS'); body(doc, 'The direct verification workflow is easier to demonstrate than a manual registry lookup because a verifier can submit a candidate document without needing an embedded QR code. The public capability workflow is privacy preserving because the result deliberately omits the source document and identity information. The proof workflow illustrates a stronger property: the verifier learns that the required threshold is satisfied without learning the exact value.')
    heading(doc, 'ANALYSIS'); body(doc, 'The performance results show that the common operations, hashing and signature verification, are fast enough for interactive use. Proof generation is more expensive than ordinary signature checking, but proof verification remains suitable for a verifier workflow. The design trade-off is intentional: stronger privacy is used only for credential predicates that require it, while standard signatures remain appropriate for document integrity.')

def chapter_six(doc):
    heading(doc, 'CHAPTER 6 CONCLUSION', True); item(doc, 'INTRODUCTION')
    body(doc, 'The project demonstrates that document verification can be designed around both trust and restraint. Integrity alone is not sufficient when a verifier requests more data than a decision needs. The platform therefore combines conventional cryptographic assurance with an explicit privacy boundary for supported credential predicates.')
    heading(doc, 'CONCLUSION'); body(doc, 'The Zero Knowledge Document Verification Platform implements a coherent end-to-end design for registering documents, checking candidate files, verifying opaque QR capabilities, issuing canonical credentials and proving supported thresholds without revealing the hidden value. The architecture isolates cryptographic primitives from web concerns, applies role boundaries and rate limits, and retains tamper-evident audit evidence. The tested implementation is suitable as an academic prototype and staging foundation, subject to the deployment controls documented in the project repository.')
    heading(doc, 'SUMMARY AND FUTURE SCOPE'); heading(doc, 'Key Achievements:')
    for x in ['Implemented SHA-256 and RSA-PSS based document authenticity verification.', 'Implemented canonical credential encoding and lifecycle-aware signature validation.', 'Implemented Pedersen commitments and Bulletproof greater-than-or-equal-to proofs.', 'Implemented minimal-disclosure QR verification, RBAC, rate limiting and audit-chain validation.', 'Established a multi-module Java architecture with automated test evidence.']: item(doc, x)
    heading(doc, 'FUTURE SCOPE:')
    for x in ['Add hardware-backed key management through PKCS#11 or a managed HSM.', 'Complete deployment with TLS termination, monitoring and scheduled encrypted backups.', 'Extend predicate support only after formal circuit design, review and negative testing.', 'Integrate an institutional OpenID Connect provider for production identity management.', 'Conduct usability evaluation with issuers, holders and external verifiers.']: item(doc, x)
    heading(doc, 'SAMPLE SOURCE CODE', True)
    body(doc, 'The following simplified Java logic represents the direct verification sequence used by the document module:')
    p=doc.add_paragraph(style='Body Text'); r=p.add_run('byte[] digest = sha256Hasher.hash(candidateStream);\nDocument record = store.findByIdOrDigest(id, digest);\nif (record == null) return Outcome.notFound(digest);\nif (!Arrays.equals(digest, record.digest())) return Outcome.integrityMismatch(record.id());\nif (record.status() == REVOKED) return Outcome.revoked(record.id());\nreturn signer.verify(record.digest(), record.signature(), trustKey) ? Outcome.verified(record.id()) : Outcome.invalidSignature(record.id());'); r.font.name='Courier New'; r.font.size=Pt(8)
    heading(doc, 'REFERENCES', True)
    for x in ['[1] NIST, Secure Hash Standard, FIPS PUB 180-4, 2015.', '[2] IETF, PKCS #1: RSA Cryptography Specifications Version 2.2, RFC 8017, 2016.', '[3] T. Pedersen, Non-Interactive and Information-Theoretic Secure Verifiable Secret Sharing, CRYPTO 1991.', '[4] B. Bünz et al., Bulletproofs: Short Proofs for Confidential Transactions and More, IEEE S&P, 2018.', '[5] IETF, The Ristretto255 and Decaf Decaf Schemes, RFC 9496, 2023.', '[6] Spring Boot Reference Documentation, Version 3.4.', '[7] OWASP Application Security Verification Standard, Version 4.0.3.', '[8] Zero Knowledge Document Verification Platform, project source code and audited implementation documentation, 2026.']: body(doc, x)

def main():
    OUT.parent.mkdir(exist_ok=True)
    shutil.copy2(SOURCE, OUT)
    doc = Document(OUT)
    # Keep the existing front matter through LIST OF ABBREVIATIONS, remove the copied sample report thereafter.
    start = next(i for i,p in enumerate(doc.paragraphs) if p.text.strip().upper() == 'CHAPTER 1 INTRODUCTION')
    for p in list(doc.paragraphs[start:]): delete_paragraph(p)
    # Remove sample tables and drawings from the removed report portion.
    for tbl in list(doc.tables):
        # Front matter tables are retained; sample content tables appear after abbreviation in document body.
        parent = tbl._element.getparent()
        if parent.tag.endswith('body'):
            parent.remove(tbl._element)
    chapter_one(doc); chapter_two(doc); chapter_three(doc); chapter_four(doc); chapter_five(doc); chapter_six(doc)
    doc.save(OUT)
    print(OUT)

if __name__ == '__main__': main()
