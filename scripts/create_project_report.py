from pathlib import Path
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY, TA_LEFT
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.platypus import (
    BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, PageBreak,
    Table, TableStyle, Image, KeepTogether, Flowable
)
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfbase import pdfmetrics
from reportlab.lib.utils import ImageReader

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output" / "pdf" / "zero_knowledge_document_verification_project_report.pdf"
ASSET = ROOT / "tmp" / "report_assets"
ASSET.mkdir(parents=True, exist_ok=True)
OUT.parent.mkdir(parents=True, exist_ok=True)

PAGE_W, PAGE_H = letter
MAR_L, MAR_R, MAR_T, MAR_B = 0.82 * inch, 0.72 * inch, 0.72 * inch, 0.68 * inch
MAROON = colors.HexColor("#7B1E22")
GOLD = colors.HexColor("#B08D36")
INK = colors.HexColor("#1F1F1F")
MUTED = colors.HexColor("#666666")

try:
    pdfmetrics.registerFont(TTFont("TimesNewRoman", "C:/Windows/Fonts/times.ttf"))
    pdfmetrics.registerFont(TTFont("TimesNewRoman-Bold", "C:/Windows/Fonts/timesbd.ttf"))
    pdfmetrics.registerFont(TTFont("TimesNewRoman-Italic", "C:/Windows/Fonts/timesi.ttf"))
    BODY = "TimesNewRoman"
    BOLD = "TimesNewRoman-Bold"
    ITALIC = "TimesNewRoman-Italic"
except Exception:
    BODY, BOLD, ITALIC = "Times-Roman", "Times-Bold", "Times-Italic"

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="Body2", fontName=BODY, fontSize=10.5, leading=15, alignment=TA_JUSTIFY, textColor=INK, spaceAfter=8))
styles.add(ParagraphStyle(name="BodyLeft", parent=styles["Body2"], alignment=TA_LEFT))
styles.add(ParagraphStyle(name="Chapter", fontName=BOLD, fontSize=16, leading=20, alignment=TA_CENTER, textColor=INK, spaceBefore=12, spaceAfter=20))
styles.add(ParagraphStyle(name="Heading1x", fontName=BOLD, fontSize=13, leading=16, textColor=MAROON, spaceBefore=12, spaceAfter=7))
styles.add(ParagraphStyle(name="Heading2x", fontName=BOLD, fontSize=11.5, leading=14, textColor=INK, spaceBefore=9, spaceAfter=5))
styles.add(ParagraphStyle(name="CoverTitle", fontName=BOLD, fontSize=18, leading=24, alignment=TA_CENTER, textColor=INK, spaceAfter=12))
styles.add(ParagraphStyle(name="CoverSub", fontName=BOLD, fontSize=12, leading=16, alignment=TA_CENTER, textColor=INK, spaceAfter=10))
styles.add(ParagraphStyle(name="Center", fontName=BODY, fontSize=11, leading=16, alignment=TA_CENTER, textColor=INK))
styles.add(ParagraphStyle(name="CenterItalic", fontName=ITALIC, fontSize=11, leading=16, alignment=TA_CENTER, textColor=MUTED))
styles.add(ParagraphStyle(name="Small", fontName=BODY, fontSize=8.5, leading=11, textColor=MUTED))
styles.add(ParagraphStyle(name="TOC", fontName=BODY, fontSize=10.5, leading=16, leftIndent=0, textColor=INK))
styles.add(ParagraphStyle(name="Caption", fontName=ITALIC, fontSize=9, leading=12, alignment=TA_CENTER, textColor=MUTED, spaceBefore=4, spaceAfter=10))

def P(text, style="Body2"):
    return Paragraph(text, styles[style])

def H1(text):
    return P(text, "Heading1x")

def H2(text):
    return P(text, "Heading2x")

def bullet(text):
    return P("&#8226; " + text, "BodyLeft")

class Placeholder(Flowable):
    def __init__(self, width=5.8*inch, height=2.0*inch, label="IMAGE TO BE INSERTED"):
        Flowable.__init__(self)
        self.width, self.height, self.label = width, height, label
    def draw(self):
        c = self.canv
        c.setStrokeColor(colors.HexColor("#A7A7A7")); c.setDash(4, 3)
        c.rect(0, 0, self.width, self.height, stroke=1, fill=0)
        c.setDash()
        c.setFillColor(MUTED); c.setFont(ITALIC, 10)
        c.drawCentredString(self.width/2, self.height/2 + 6, self.label)
        c.setFont(BODY, 8); c.drawCentredString(self.width/2, self.height/2 - 9, "Reserved space - supplied figure will be placed here")

def image_or_placeholder(path, width, height, label):
    if Path(path).exists():
        return Image(str(path), width=width, height=height)
    return Placeholder(width, height, label)

def header_footer(canvas, doc):
    canvas.saveState()
    page = canvas.getPageNumber()
    if page > 1:
        canvas.setStrokeColor(colors.HexColor("#D9D9D9")); canvas.setLineWidth(0.5)
        canvas.line(MAR_L, PAGE_H - 0.48*inch, PAGE_W - MAR_R, PAGE_H - 0.48*inch)
        canvas.setFont(BOLD, 8); canvas.setFillColor(MAROON)
        canvas.drawString(MAR_L, PAGE_H - 0.36*inch, "ZERO-KNOWLEDGE DOCUMENT VERIFICATION PLATFORM")
        canvas.setFont(BODY, 8); canvas.setFillColor(MUTED)
        canvas.drawRightString(PAGE_W - MAR_R, PAGE_H - 0.36*inch, f"Project Report  |  Page {page}")
        canvas.setStrokeColor(colors.HexColor("#D9D9D9"))
        canvas.line(MAR_L, 0.44*inch, PAGE_W - MAR_R, 0.44*inch)
        canvas.setFont(BODY, 8); canvas.setFillColor(MUTED)
        canvas.drawCentredString(PAGE_W/2, 0.27*inch, str(page))
    canvas.restoreState()

doc = BaseDocTemplate(str(OUT), pagesize=letter, leftMargin=MAR_L, rightMargin=MAR_R, topMargin=MAR_T+0.08*inch, bottomMargin=MAR_B)
frame = Frame(MAR_L, MAR_B, PAGE_W-MAR_L-MAR_R, PAGE_H-MAR_T-MAR_B, id="normal")
doc.addPageTemplates([PageTemplate(id="report", frames=frame, onPage=header_footer)])
story = []

eec_logo = ASSET / "easwari_logo.png"
anna_logo = ASSET / "anna_logo.png"

# Cover
story += [Spacer(1, 0.18*inch), Table([[image_or_placeholder(anna_logo, 1.55*inch, 0.9*inch, "ANNA UNIVERSITY LOGO"), image_or_placeholder(eec_logo, 2.2*inch, 0.9*inch, "EASWARI LOGO")]], colWidths=[2.8*inch, 2.8*inch], hAlign="CENTER", style=TableStyle([("VALIGN",(0,0),(-1,-1),"MIDDLE"), ("ALIGN",(0,0),(-1,-1),"CENTER")])), Spacer(1, 0.55*inch)]
story += [P("ZERO-KNOWLEDGE DOCUMENT VERIFICATION PLATFORM", "CoverTitle"), P("(Privacy-Preserving Document and Credential Verification)", "CoverSub"), Spacer(1, 0.18*inch), P("A PROJECT REPORT", "CoverSub"), Spacer(1, 0.25*inch), P("Submitted by", "CenterItalic"), Spacer(1, 0.08*inch), P("P SAKTHI SABAREESH", "CoverSub"), P("[REGISTER NUMBER]", "Center"), Spacer(1, 0.22*inch), P("in partial fulfilment for the award of the degree", "CenterItalic"), Spacer(1, 0.15*inch), P("BACHELOR OF ENGINEERING", "CoverSub"), P("IN", "Center"), P("COMPUTER SCIENCE AND ENGINEERING", "CoverSub"), Spacer(1, 0.08*inch), P("II YEAR CSE", "CoverSub"), P("EASWARI ENGINEERING COLLEGE, RAMAPURAM", "CoverSub"), P("(AUTONOMOUS)", "CoverSub"), Spacer(1, 0.18*inch), P("ANNA UNIVERSITY: CHENNAI 600 025", "CoverSub"), P("2026 - 2027", "Center")]
story.append(PageBreak())

# Declaration
story += [P("DECLARATION", "Chapter"), P("I hereby declare that the project report entitled <b>\"Zero-Knowledge Document Verification Platform\"</b> is prepared as part of the academic project work carried out by me under the guidance of Mrs. S. Sindhu, Assistant Professor, Department of Computer Science and Engineering, Easwari Engineering College. The report describes the design and implementation of a privacy-preserving platform for document integrity checking, credential issuance and zero-knowledge predicate verification.") , Spacer(1,0.18*inch), P("The implementation details, test results and technical claims in this report are based on the project repository, project documentation and recorded engineering work. Institutional approval, register number, signatures and dates are to be completed before formal submission.") , Spacer(1,0.55*inch), P("Place: Chennai", "BodyLeft"), P("Date: ____________________", "BodyLeft"), Spacer(1,0.55*inch), P("Signature of the Student: ______________________________", "BodyLeft"), P("P Sakthi Sabareesh", "BodyLeft"), PageBreak()]

# Certificate
story += [P("BONAFIDE CERTIFICATE", "Chapter"), P("This is to certify that the project report entitled <b>\"Zero-Knowledge Document Verification Platform\"</b> is a record of the project work carried out by <b>P Sakthi Sabareesh</b> of the Department of Computer Science and Engineering, Easwari Engineering College, during the academic year 2026 - 2027, under the supervision of Mrs. S. Sindhu, Assistant Professor, Department of Computer Science and Engineering.") , Spacer(1,0.35*inch)]
cert = [[P("__________________________", "Center"), P("__________________________", "Center")], [P("Mrs. S. Sindhu", "Center"), P("Cloudin", "Center")], [P("Supervisor\nAssistant Professor, CSE", "Center"), P("Head of the Department\nCSE", "Center")], [P("\nSignature / Seal", "Center"), P("\nSignature / Seal", "Center")]]
story += [Table(cert, colWidths=[2.8*inch,2.8*inch], style=TableStyle([("VALIGN",(0,0),(-1,-1),"TOP"), ("ALIGN",(0,0),(-1,-1),"CENTER"), ("TOPPADDING",(0,0),(-1,-1),4), ("BOTTOMPADDING",(0,0),(-1,-1),5)])), Spacer(1,0.45*inch), P("Internal Examiner: ____________________    External Examiner: ____________________", "BodyLeft"), P("Submitted for the project work examination held on: ____________________", "BodyLeft"), PageBreak()]

# Acknowledgement
story += [P("ACKNOWLEDGEMENT", "Chapter"), P("I express my sincere gratitude to the management and Principal of Easwari Engineering College for providing the academic environment and facilities required to complete this project. I thank the Head of the Department, Cloudin, Department of Computer Science and Engineering, for the encouragement and institutional support provided during the work.") , P("I express my deepest gratitude to my supervisor, Mrs. S. Sindhu, Assistant Professor, Department of Computer Science and Engineering, for her continuous guidance, technical suggestions and constructive feedback throughout the planning, implementation and documentation of the project.") , P("I also acknowledge the maintainers of Java, Spring Boot, React, TypeScript, PostgreSQL, SQLite, Flyway, cryptographic libraries and the supporting open-source ecosystem used in this work. Finally, I thank my family, classmates and friends for their encouragement and support.") , Spacer(1,0.45*inch), P("P Sakthi Sabareesh", "BodyLeft"), PageBreak()]

# Abstract
story += [P("ABSTRACT", "Chapter"), P("Digital documents and credentials are routinely shared to prove qualifications, identity-related attributes and eligibility. Conventional verification often requires a person to disclose an entire document even when the verifier needs only one fact. This creates unnecessary exposure of personal information and introduces risks associated with document tampering, forged credentials, uncontrolled copying and weak auditability.") , P("The Zero-Knowledge Document Verification Platform is a modular Java and React application designed to combine document integrity verification with privacy-preserving credential proofs. The platform uses streaming SHA-256 digests to identify document content and RSA-PSS signatures to support authenticity checks. Credentials use canonical encodings so that signatures are stable and verifiable. For supported numerical predicates, Pedersen commitments and Bulletproof range proofs enable a holder to prove a threshold statement without disclosing the underlying value.") , P("The system separates issuer, holder, verifier and administrator responsibilities through JWT-based authentication and role-based authorization. Public verification uses opaque capability tokens with expiry and rate limiting. PostgreSQL is the intended production database, while SQLite supports local demonstrations. The backend is organized as a modular Spring Boot monolith, and the React frontend provides role-specific workflows for document upload, credential management, proof generation, public verification and audit exploration.") , P("The project demonstrates that privacy-preserving verification can be implemented with a maintainable full-stack architecture suitable for academic demonstration and a controlled pilot. Its main limitations are the need for production-grade key custody, a durable artifact store, a verified TLS edge, complete restore testing and final operational hardening.") , Spacer(1,0.15*inch), P("<b>Keywords:</b> Zero-Knowledge Proofs, Pedersen Commitment, Bulletproofs, RSA-PSS, SHA-256, Credential Verification, Privacy, Document Integrity.", "BodyLeft"), PageBreak()]

# TOC exact structure from sample
story += [P("TABLE OF CONTENTS", "Chapter")]
toc = [
    ("Chapter 1", "Introduction"), ("1.1", "Overview"), ("1.2", "Objectives"), ("1.3", "Challenges in the Domain"), ("1.4", "Motivation"), ("1.5", "Organization of the Report"),
    ("Chapter 2", "Related Work"), ("2.1", "Literature and Technical Background"), ("2.2", "Existing Technology"), ("2.3", "Inference of Review"), ("2.4", "Extraction from Review"),
    ("Chapter 3", "System Analysis"), ("3.1", "Problem Definition"), ("3.2", "Proposed Solution"), ("3.3", "Software Components"), ("3.4", "Hardware and Runtime Requirements"), ("3.5", "Use Cases"),
    ("Chapter 4", "System Design"), ("4.1", "Architecture"), ("4.2", "Module Design"), ("4.3", "Data Flow and Trust Boundaries"), ("4.4", "Database and Credential Lifecycle"), ("4.5", "Security Design"),
    ("Chapter 5", "Implementation"), ("5.1", "Technology Stack"), ("5.2", "Document Integrity and Signatures"), ("5.3", "Credentials and Zero-Knowledge Proofs"), ("5.4", "QR and Public Verification"), ("5.5", "API and Frontend"), ("5.6", "CI/CD, Backup and Deployment Hardening"),
    ("Chapter 6", "System Testing"), ("6.1", "Testing Strategy"), ("6.2", "Test Coverage"), ("6.3", "Adversarial Testing"), ("6.4", "Performance and Concurrency"),
    ("Chapter 7", "Outputs and Demonstration"), ("7.1", "Expected Workflow Outputs"), ("7.2", "API Examples and Result Semantics"),
    ("Chapter 8", "Results and Discussion"), ("8.1", "Reported Test Results"), ("8.2", "Performance Measurements"), ("8.3", "Security and Operational Assessment"), ("8.4", "Limitations and Risk Register"),
    ("Chapter 9", "Conclusion and Future Enhancements"), ("Appendix A", "API Reference"), ("Appendix B", "Build, Test and Run Commands"), ("Appendix C", "Test Matrix and Deployment Checklist"), ("References", "References")]
toc_pages = {
    "Chapter 1": 10, "1.1": 10, "1.2": 10, "1.3": 10, "1.4": 10, "1.5": 10,
    "Chapter 2": 11, "2.1": 11, "2.2": 11, "2.3": 11, "2.4": 11,
    "Chapter 3": 12, "3.1": 12, "3.2": 12, "3.3": 12, "3.4": 13, "3.5": 13,
    "Chapter 4": 13, "4.1": 13, "4.2": 13, "4.3": 14, "4.4": 14, "4.5": 14,
    "Chapter 5": 15, "5.1": 15, "5.2": 15, "5.3": 15, "5.4": 16, "5.5": 16, "5.6": 16,
    "Chapter 6": 17, "6.1": 17, "6.2": 17, "6.3": 17, "6.4": 17,
    "Chapter 7": 18, "7.1": 18, "7.2": 19,
    "Chapter 8": 20, "8.1": 20, "8.2": 20, "8.3": 21, "8.4": 21,
    "Chapter 9": 22, "Appendix A": 23, "Appendix B": 24, "Appendix C": 25, "References": 26,
}
for num, title in toc:
    indent = 16 if num[0].isdigit() and not num.startswith("Chapter") else 0
    line = f"<b>{num}</b>&nbsp;&nbsp; {title}<font color='#666666'> .......... {toc_pages.get(num, '')}</font>"
    story.append(Paragraph(line, ParagraphStyle("tocline", parent=styles["TOC"], leftIndent=indent)))
story.append(PageBreak())

story += [P("LIST OF FIGURES", "Chapter"), P("Figure 4.1  Overall document and credential verification flow"), P("Figure 4.2  Logical system architecture"), P("Figure 4.3  Credential predicate verification lifecycle"), P("Figure 5.1  Issuer document studio and upload workflow"), P("Figure 7.1  Public QR verification result screen"), P("Figure 7.2  Holder credential wallet and proof workflow"), PageBreak()]
story += [P("LIST OF TABLES", "Chapter"), P("Table 3.1  Functional requirements"), P("Table 3.2  Non-functional requirements"), P("Table 4.1  Project modules"), P("Table 5.1  Technology stack"), P("Table 6.1  Test coverage matrix"), P("Table 8.1  Benchmark measurements"), P("Table 8.2  Principal risks and mitigations"), PageBreak()]

def chapter(title):
    story.append(P(title, "Chapter"))
def section(title, paras):
    story.append(H1(title))
    for x in paras: story.append(P(x))

chapter("CHAPTER 1\nINTRODUCTION")
section("1.1 OVERVIEW", ["The Zero-Knowledge Document Verification Platform addresses a practical tension in digital verification: a verifier often needs to establish one fact, while the holder is asked to reveal an entire document. The platform combines document-level authenticity with selective, privacy-preserving attribute verification.", "The project is implemented as a modular Spring Boot application with a React and TypeScript frontend. Issuers register documents and issue signed credentials, holders retain credentials and generate supported proofs, verifiers validate documents or predicates, and administrators manage trust material and audit evidence."])
section("1.2 OBJECTIVES", ["The primary objective is to provide a reliable workflow for registering a document, generating a tamper-evident digest, associating it with a credential, and verifying its integrity later. A second objective is to support threshold predicates such as an attribute being greater than or equal to a required value without revealing the attribute itself.", "The project also aims to demonstrate secure modular design: explicit role boundaries, canonical signed data, controlled key lifecycle, defensive input validation, rate limiting for cryptographic workloads, audit events and a deployment path that can be operated on a small budget."])
section("1.3 CHALLENGES IN THE DOMAIN", ["Document verification systems must protect both authenticity and privacy. Hashes alone do not establish who issued a document, while signatures without canonical encoding can be ambiguous. Zero-knowledge proofs add a second correctness boundary: the proof must be linked to the right commitment, credential and trust context.", "The system must also handle real operational concerns such as expired capabilities, revoked credentials, duplicate submissions, storage failures, database concurrency, key rotation and abuse of CPU-intensive proof operations."])
section("1.4 MOTIVATION", ["Academic certificates, professional qualifications and compliance records are frequently copied into systems that retain more personal information than necessary. A holder should be able to prove eligibility while minimizing disclosure. This project explores a practical path from cryptographic primitives to a usable web application."])
section("1.5 ORGANIZATION OF THE REPORT", ["Chapter 1 introduces the problem and motivation. Chapter 2 reviews relevant technologies. Chapter 3 analyses requirements and use cases. Chapter 4 presents architecture, modules, data flow and security boundaries. Chapter 5 explains implementation. Chapters 6 to 8 describe testing, demonstration outputs, results and limitations. Chapter 9 concludes the report and proposes future enhancements. Appendices provide API, command and deployment references."])
story.append(PageBreak())

chapter("CHAPTER 2\nRELATED WORK")
section("2.1 LITERATURE AND TECHNICAL BACKGROUND", ["Traditional document verification normally compares a submitted document with an issuer record or checks a signature. Verifiable credentials extend this model by separating the credential holder, issuer and verifier. Zero-knowledge proofs add selective disclosure: a verifier can check a statement without receiving the witness that makes the statement true.", "Pedersen commitments provide hiding and binding properties under the chosen group assumptions. Bulletproof-style range proofs allow a prover to demonstrate that a committed integer lies within a range. In this project, the cryptographic engine is used through a narrow application boundary rather than being spread through controllers and UI code."])
section("2.2 EXISTING TECHNOLOGY", ["The backend uses Java 21 and Spring Boot 3.4.3. JDBC and Flyway provide persistence and schema migration. PostgreSQL is the intended production database and SQLite supports local development. The frontend uses React, TypeScript, Vite, Tailwind CSS and browser QR capabilities. Docker packages the backend and static frontend into a non-root runtime container.", "The platform also uses RSA-PSS for signatures, SHA-256 for content digests, JWTs for authentication, Bucket4j-based controls for rate limiting, ZXing for QR generation and automated tests using JUnit, Spring Security test support, Testcontainers and Vitest/React Testing Library."])
section("2.3 INFERENCE OF REVIEW", ["The project combines evidence from cryptographic tests, storage tests, API tests, security tests, frontend tests and deployment documentation. The correct interpretation is conditional: passing unit tests establish properties of isolated components, but production readiness additionally requires clean-room builds, real PostgreSQL migrations, external identity-provider validation, deployment smoke tests and restore drills."])
section("2.4 EXTRACTION FROM REVIEW", ["The central engineering lesson is that privacy features do not remove the need for ordinary production discipline. Canonical data, database constraints, idempotency, observability, secure defaults and operational runbooks are as important as the proof system. The implementation therefore keeps a modular monolith and postpones distributed infrastructure until measured demand requires it."])
story.append(PageBreak())

chapter("CHAPTER 3\nSYSTEM ANALYSIS")
section("3.1 PROBLEM DEFINITION", ["A user who owns a certificate may need to prove a qualification, age threshold or accredited status to another party. Sending the complete certificate exposes unrelated personal information and creates copies that are difficult to control. At the same time, a verifier needs confidence that the document was issued by a trusted issuer, has not been modified and has not been revoked."])
section("3.2 PROPOSED SOLUTION", ["The platform stores document metadata and cryptographic anchors, issues signed credentials, and exposes separate workflows for issuers, holders, verifiers and administrators. A public capability token can point a verifier to a controlled verification operation. A holder can generate a predicate proof against a stored commitment, allowing the verifier to validate the statement without learning the raw attribute."])
section("3.3 SOFTWARE COMPONENTS", ["The repository is organized into six Maven modules below the root platform: core crypto, document, credential, ZKP, keys and API. The frontend is a separate React application built into static assets served by the API jar. This structure keeps cryptographic primitives independent of Spring and allows domain services to be tested without HTTP."])
data=[["Component","Responsibility"],["zkdoc-core-crypto","Digests, RSA-PSS signing and verification"],["zkdoc-document","Document identity, upload validation and artifact storage"],["zkdoc-credential","Issuer, holder credential lifecycle and canonical encoding"],["zkdoc-zkp","Commitments, predicate proof generation and verification"],["zkdoc-keys","Key registry, trust and lifecycle"],["zkdoc-api","HTTP controllers, security, persistence wiring and operations"],["Frontend","Role-specific React workflows and public verification"]]
story += [Table(data, colWidths=[1.55*inch,4.05*inch], repeatRows=1, style=TableStyle([("BACKGROUND",(0,0),(-1,0),MAROON),("TEXTCOLOR",(0,0),(-1,0),colors.white),("FONTNAME",(0,0),(-1,-1),BODY),("FONTNAME",(0,0),(-1,0),BOLD),("FONTSIZE",(0,0),(-1,-1),9),("LEADING",(0,0),(-1,-1),12),("GRID",(0,0),(-1,-1),0.35,colors.HexColor("#BBBBBB")),("VALIGN",(0,0),(-1,-1),"TOP"),("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, colors.HexColor("#F7F3F0")]),("PADDING",(0,0),(-1,-1),5)])), P("Table 3.1  Project software components", "Caption")]
section("3.4 HARDWARE AND RUNTIME REQUIREMENTS", ["For local demonstration, the application can run on a modern laptop with Java 21, Node.js and a local SQLite database. A pilot deployment should use PostgreSQL, a Linux container host with at least 2 vCPU and 2 to 4 GB RAM, encrypted artifact storage and a TLS reverse proxy. The exact capacity must be confirmed with mixed-workload load testing rather than cryptographic microbenchmarks alone."])
section("3.5 USE CASES", ["The issuer uploads and registers a document, issues a credential and creates a capability. The holder receives or stores a credential, selects a supported predicate and generates a proof. The verifier scans a QR code or enters a token and inspects signature, digest, lifecycle and predicate results. The administrator manages keys, audit events, recovery checks and operational health."])
story.append(PageBreak())

chapter("CHAPTER 4\nSYSTEM DESIGN")
section("4.1 ARCHITECTURE", ["The platform follows a modular monolith architecture. The browser communicates with the Spring Boot API over HTTP. The API coordinates domain services and repositories. PostgreSQL stores structured records, while document artifacts are stored through an artifact-store port. Cryptographic operations are isolated behind services that receive typed inputs and return typed results."])
story += [Placeholder(5.7*inch,2.35*inch,"FIGURE 4.1 - OVERALL DOCUMENT AND CREDENTIAL VERIFICATION FLOW"), P("Figure 4.1  Overall document and credential verification flow", "Caption"), Placeholder(5.7*inch,2.35*inch,"FIGURE 4.2 - LOGICAL SYSTEM ARCHITECTURE"), P("Figure 4.2  Logical system architecture", "Caption")]
section("4.2 MODULE DESIGN", ["The module design separates low-level cryptography from application policy. The document module owns digesting, content validation and artifact lifecycle. The credential module owns canonical encoding, issuer and holder state. The ZKP module owns commitment and predicate operations. The keys module resolves trusted public keys and lifecycle status. The API module is the delivery and integration boundary."])
section("4.3 DATA FLOW AND TRUST BOUNDARIES", ["Trust begins at the issuer boundary, where a document is received, validated and hashed. A credential is signed using an issuer key and bound to the relevant document or attributes. The holder controls the witness needed for proof generation. The verifier receives a presentation and checks it against server-side anchors. The server does not need to receive the hidden attribute value for a supported predicate.", "The main trust boundaries are the browser-to-API boundary, API-to-database boundary, API-to-artifact-storage boundary, signing-key boundary and public-verification boundary. Each boundary requires authentication, authorization, integrity checks and controlled error handling appropriate to its data."])
section("4.4 DATABASE AND CREDENTIAL LIFECYCLE", ["The database contains documents, artifacts, credentials, attributes, commitments, verification capabilities, key registry entries, lifecycle events and security audit events. Credential states include active, suspended, revoked or expired. Capability tokens are opaque, time-bound and independently revocable. Production deployment requires PostgreSQL because SQLite is not suitable for multi-node concurrent operation."])
section("4.5 SECURITY DESIGN", ["Security uses stateless bearer JWT authentication, role-based authorization, object ownership checks, validation annotations, sanitized error responses, CSP and other response headers, rate limiting and bounded concurrency around expensive proof operations. Production startup validation is intended to fail closed when demo seeding, weak database passwords, missing storage encryption or development key providers are configured."])
story.append(PageBreak())

chapter("CHAPTER 5\nIMPLEMENTATION")
section("5.1 TECHNOLOGY STACK", ["The backend is written in Java 21 using Spring Boot 3.4.3 and Maven. The frontend uses React and TypeScript with Vite and Tailwind CSS. PostgreSQL and SQLite are supported through separate Flyway migration trees. Docker uses a multi-stage Eclipse Temurin 21 build and runtime image. CI uses GitHub Actions with dependency, secret, SBOM, CodeQL and container checks."])
stack=[["Layer","Technology"],["Language and runtime","Java 21, Node.js 24 toolchain"],["Backend","Spring Boot, Spring Security, JDBC, Actuator"],["Frontend","React, TypeScript, Vite, Tailwind CSS"],["Database","PostgreSQL 16 for production; SQLite for local demo"],["Cryptography","SHA-256, RSA-PSS, Pedersen commitments, Bulletproof predicates"],["Testing","JUnit 5, Spring Security tests, Testcontainers, Vitest and RTL"],["Operations","Docker Compose, Flyway, GitHub Actions, SBOM and scanners"]]
story += [Table(stack, colWidths=[1.65*inch,3.95*inch], repeatRows=1, style=TableStyle([("BACKGROUND",(0,0),(-1,0),MAROON),("TEXTCOLOR",(0,0),(-1,0),colors.white),("FONTNAME",(0,0),(-1,0),BOLD),("FONTNAME",(0,1),(-1,-1),BODY),("FONTSIZE",(0,0),(-1,-1),9),("GRID",(0,0),(-1,-1),0.35,colors.HexColor("#BBBBBB")),("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, colors.HexColor("#F7F3F0")]),("PADDING",(0,0),(-1,-1),5)])), P("Table 5.1  Technology stack", "Caption")]
section("5.2 DOCUMENT INTEGRITY AND SIGNATURES", ["Uploaded content is processed as a stream to calculate a SHA-256 digest. The digest is stored with document metadata and used to detect later changes. RSA-PSS signatures provide issuer authenticity for canonical credential encodings. The implementation validates content type and file structure before accepting an artifact and protects storage paths against traversal."])
section("5.3 CREDENTIALS AND ZERO-KNOWLEDGE PROOFS", ["A credential contains canonical data that can be signed and later reconstructed for verification. Numerical attributes can be committed using C = vG + rH, where v is the attribute and r is a cryptographically generated blinding scalar. A holder can generate a proof for a supported threshold predicate. The verifier checks the proof against the commitment and trusted credential context without receiving v or r."])
story += [Placeholder(5.7*inch,2.35*inch,"FIGURE 4.3 - CREDENTIAL PREDICATE VERIFICATION LIFECYCLE"), P("Figure 4.3  Credential predicate verification lifecycle", "Caption")]
section("5.4 QR AND PUBLIC VERIFICATION", ["Verification capabilities use opaque random tokens rather than embedding sensitive document fields in a QR code. Tokens have lifecycle status and expiry. The public endpoint is rate-limited and returns a bounded verification result. QR images can be generated for direct public verification URLs and scanned through the browser or entered manually."])
section("5.5 API AND FRONTEND", ["The React application defines public routes for login and verification and protected workspaces for holders, issuers and administrators. Route guards check authentication and role requirements. A shared API client adds correlation identifiers, bearer authorization and normalized error handling. The backend serves the built SPA from the executable jar for a compact deployment."])
section("5.6 CI/CD, BACKUP AND DEPLOYMENT HARDENING", ["The CI pipeline pins action revisions, runs backend and frontend checks, generates SBOMs, scans dependencies and container images, and checks for secrets. The runtime container runs as a non-root UID with dropped capabilities, a read-only root filesystem and bounded temporary storage. PostgreSQL and artifacts require a coordinated backup and restore process before production use."])
story.append(PageBreak())

chapter("CHAPTER 6\nSYSTEM TESTING")
section("6.1 TESTING STRATEGY", ["Testing is organized at domain, cryptographic, persistence, API, security and frontend component levels. Important security tests cover invalid signatures, tampered documents, authorization boundaries, lifecycle transitions, proof rejection and capability expiry. Integration tests should use PostgreSQL for release confidence, while SQLite remains useful for fast local feedback."])
section("6.2 TEST COVERAGE", ["The repository contains extensive JUnit and frontend test sources. The project profile reports 204 backend tests and 259 frontend tests, but a fresh run in the current checkout did not reproduce that result: storage tests failed under the Windows sandbox temp-file move behavior and frontend commands could not resolve local tool binaries. The final submission should include a clean-run evidence file with the exact commit and environment."])
matrix=[["Area","Representative checks","Release expectation"],["Crypto","Digest, RSA-PSS, commitments, proof validity","All pass"],["Storage","Atomic write, retrieval, tamper detection, encryption","All pass on Windows and Linux"],["Authorization","Role, ownership, revoked state, object access","Matrix pass"],["Database","Fresh and upgraded PostgreSQL migrations","Pass with constraints"],["Frontend","Typecheck, lint, component and route behavior","All pass from npm ci"],["Operations","Health, backup, restore, container smoke test","Demonstrated"]]
story += [Table(matrix, colWidths=[1.1*inch,2.9*inch,1.6*inch], repeatRows=1, style=TableStyle([("BACKGROUND",(0,0),(-1,0),MAROON),("TEXTCOLOR",(0,0),(-1,0),colors.white),("FONTNAME",(0,0),(-1,0),BOLD),("FONTNAME",(0,1),(-1,-1),BODY),("FONTSIZE",(0,0),(-1,-1),8.7),("GRID",(0,0),(-1,-1),0.35,colors.HexColor("#BBBBBB")),("VALIGN",(0,0),(-1,-1),"TOP"),("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, colors.HexColor("#F7F3F0")]),("PADDING",(0,0),(-1,-1),5)])), P("Table 6.1  Test coverage matrix", "Caption")]
section("6.3 ADVERSARIAL TESTING", ["Adversarial coverage should include altered document bytes, wrong credential identifiers, mismatched commitments, invalid proof statements, replayed or expired capabilities, malformed bearer tokens, unauthorized role combinations, duplicate lifecycle requests and path manipulation attempts. The defensive objective is to reject unsafe input without disclosing sensitive values or internal stack traces."])
section("6.4 PERFORMANCE AND CONCURRENCY", ["Recorded engineering measurements indicate that SHA-256 and RSA verification are inexpensive compared with Bulletproof proof generation and verification. Therefore, concurrency limits and rate limits protect the API from CPU starvation. A production benchmark must use mixed workflows and measure p95/p99 latency, database pool usage, memory, garbage collection and artifact I/O."])
story.append(PageBreak())

chapter("CHAPTER 7\nOUTPUTS AND DEMONSTRATION")
section("7.1 EXPECTED WORKFLOW OUTPUTS", ["The issuer workflow produces a registered document record, digest, signature and credential. The holder workflow displays credential attributes, selects a predicate and produces a proof presentation. The verifier workflow returns separate outcomes for capability status, document integrity, credential signature, issuer trust, expiry and predicate validity. The administrator workflow exposes key lifecycle and audit information."])
story += [Placeholder(5.7*inch,2.45*inch,"FIGURE 5.1 - ISSUER DOCUMENT STUDIO AND UPLOAD WORKFLOW"), P("Figure 5.1  Issuer document studio and upload workflow", "Caption"), Placeholder(5.7*inch,2.45*inch,"FIGURE 7.1 - PUBLIC QR VERIFICATION RESULT SCREEN"), P("Figure 7.1  Public QR verification result screen", "Caption"), Placeholder(5.7*inch,2.45*inch,"FIGURE 7.2 - HOLDER CREDENTIAL WALLET AND PROOF WORKFLOW"), P("Figure 7.2  Holder credential wallet and proof workflow", "Caption")]
section("7.2 API EXAMPLES AND RESULT SEMANTICS", ["A successful public verification response should be understandable without exposing internal database identifiers or raw credential attributes. A failed response should distinguish invalid or expired capability, revoked credential, digest mismatch, signature failure, unsupported predicate and service error. Authenticated API responses should include a correlation identifier so that an operator can trace a failed workflow without logging secrets."])
story.append(PageBreak())

chapter("CHAPTER 8\nRESULTS AND DISCUSSION")
section("8.1 REPORTED TEST RESULTS", ["The supplied project documentation describes a high-fidelity working prototype with seeded synthetic data, automated tests and a containerized topology. The engineering review found strong test intent and extensive supporting documentation, but the result must be re-established from a clean checkout before it is presented as a final release metric. A test count without commit, tool versions, skipped tests and environment details is not sufficient evidence."])
section("8.2 PERFORMANCE MEASUREMENTS", ["The project profile reports isolated measurements including SHA-256 digest throughput, RSA-PSS signing and verification, Pedersen commitment generation, Bulletproof proof generation and verification, and audit-chain operations. These measurements show that ZKP operations are the dominant CPU cost. They justify bounded concurrency and endpoint-specific throttling, but they do not alone establish capacity for a complete user workflow."])
bench=[["Operation","Reported mean latency","Engineering interpretation"],["SHA-256, 5 MB","3.508 ms","Low cost; stream content"],["RSA-PSS signing","1.013 ms","Suitable for synchronous issuance at pilot scale"],["Pedersen commitment","0.363 ms","Low relative cost"],["Bulletproof generation","72.758 ms","CPU-bound; bound concurrency"],["Bulletproof verification","12.007 ms","Rate-limit public or verifier access"],["Audit append","4.173 ms","Protect sequential contention"]]
story += [Table(bench, colWidths=[1.8*inch,1.35*inch,2.45*inch], repeatRows=1, style=TableStyle([("BACKGROUND",(0,0),(-1,0),MAROON),("TEXTCOLOR",(0,0),(-1,0),colors.white),("FONTNAME",(0,0),(-1,0),BOLD),("FONTNAME",(0,1),(-1,-1),BODY),("FONTSIZE",(0,0),(-1,-1),8.8),("GRID",(0,0),(-1,-1),0.35,colors.HexColor("#BBBBBB")),("VALIGN",(0,0),(-1,-1),"TOP"),("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, colors.HexColor("#F7F3F0")]),("PADDING",(0,0),(-1,-1),5)])), P("Table 8.1  Reported benchmark measurements", "Caption")]
section("8.3 SECURITY AND OPERATIONAL ASSESSMENT", ["The project has a strong defensive baseline for an academic implementation: explicit authentication and roles, object ownership concepts, security headers, validation, audit events, container hardening and CI supply-chain controls. The remaining gap is operational proof. Production requires a real identity provider, safe profile defaults, durable encrypted artifacts, TLS termination, key custody, restore drills and monitoring."])
section("8.4 LIMITATIONS AND RISK REGISTER", ["The implementation is intentionally limited to documented predicate types rather than a general-purpose proof language. Private signing keys are not HSM-backed. SQLite is not a production multi-node database. Local filesystem artifacts do not provide automatic cross-instance durability. In-memory rate limiting is not a distributed quota. These limitations are acceptable for a controlled pilot when explicitly enforced, but they must not be hidden behind production-sounding claims."])
risks=[["Risk","Impact","Mitigation"],["Demo data enabled in production profile","Data contamination and unauthorized test identities","Fail-closed defaults and profile tests"],["Dropped database foreign keys","Orphaned or inconsistent verification records","Canonical schema migration and constraint validation"],["Local artifact volume loss","Metadata/artifact divergence","Encrypted object storage or single-instance policy plus restore drill"],["Filesystem signing keys","Credential forgery after host compromise","KMS/HSM or hardened secret custody and rotation"],["Unverified frontend/toolchain state","Untested user-facing defects","Clean npm ci, typecheck, lint, test and build gate"]]
story += [Table(risks, colWidths=[1.7*inch,1.85*inch,2.05*inch], repeatRows=1, style=TableStyle([("BACKGROUND",(0,0),(-1,0),MAROON),("TEXTCOLOR",(0,0),(-1,0),colors.white),("FONTNAME",(0,0),(-1,0),BOLD),("FONTNAME",(0,1),(-1,-1),BODY),("FONTSIZE",(0,0),(-1,-1),8.5),("GRID",(0,0),(-1,-1),0.35,colors.HexColor("#BBBBBB")),("VALIGN",(0,0),(-1,-1),"TOP"),("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, colors.HexColor("#F7F3F0")]),("PADDING",(0,0),(-1,-1),5)])), P("Table 8.2  Principal risks and mitigations", "Caption")]
story.append(PageBreak())

chapter("CHAPTER 9\nCONCLUSION AND FUTURE ENHANCEMENTS")
section("CONCLUSION", ["The Zero-Knowledge Document Verification Platform demonstrates a practical integration of document integrity, digital signatures, verifiable credentials, role-based access and privacy-preserving predicates. The modular monolith is appropriate for the project's current academic and pilot scale and avoids unnecessary distributed infrastructure.", "The strongest next step is not a rewrite. It is disciplined hardening: make production configuration fail closed, make test evidence reproducible, restore database constraints, secure key and artifact custody, implement the TLS edge, and exercise backup recovery. Once these controls are demonstrated, the platform can support a small institutional pilot with clearly stated limits."])
section("FUTURE ENHANCEMENTS", ["Future work may include an external OIDC provider with PKCE and rotating sessions, KMS/HSM-backed signing, encrypted object storage, organization-level tenancy, shared rate limiting, richer predicate types, selective disclosure for non-numerical attributes, audit export and independent verification tooling. These should be implemented only when a measured requirement justifies the added operational complexity."])
story.append(PageBreak())

chapter("APPENDIX A\nAPI REFERENCE")
story += [P("The API is organized around public verification, document registration, credentials, holder workflows, predicate verification, capability tokens and administration. The exact request and response schemas should be taken from the current controller DTOs and API contract documentation at submission time.")]
api=[["Route family","Role","Purpose"],["/api/public/**","Public","Capability-based document or credential verification"],["/api/documents","ISSUER","Upload and register a document"],["/api/credentials","ISSUER","Issue or manage credentials"],["/api/holder/**","HOLDER / ADMIN","Holder credential and proof generation"],["/api/v2/zkp/**","VERIFIER","Verify predicate presentations"],["/api/admin/**","ADMIN","Keys, audit and recovery operations"]]
story += [Table(api, colWidths=[1.6*inch,1.25*inch,2.75*inch], repeatRows=1, style=TableStyle([("BACKGROUND",(0,0),(-1,0),MAROON),("TEXTCOLOR",(0,0),(-1,0),colors.white),("FONTNAME",(0,0),(-1,0),BOLD),("FONTNAME",(0,1),(-1,-1),BODY),("FONTSIZE",(0,0),(-1,-1),9),("GRID",(0,0),(-1,-1),0.35,colors.HexColor("#BBBBBB")),("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, colors.HexColor("#F7F3F0")]),("PADDING",(0,0),(-1,-1),5)]))]
story.append(PageBreak())

chapter("APPENDIX B\nBUILD, TEST AND RUN COMMANDS")
story += [P("Backend build and test:"), P("./mvnw clean test", "BodyLeft"), P("Frontend verification:"), P("cd frontend\nnpm ci\nnpm run typecheck\nnpm run lint\nnpm run test:run\nnpm run build", "BodyLeft"), P("Local demonstration:"), P("./scripts/demo-setup.ps1", "BodyLeft"), P("Containerized staging:"), P("docker compose up --build", "BodyLeft"), P("Before deployment, configure database credentials, storage encryption keys, backup keys, an external identity provider, TLS termination and a documented restore procedure.")]
story.append(PageBreak())

chapter("APPENDIX C\nTEST MATRIX AND DEPLOYMENT CHECKLIST")
for text in ["Build passes from a clean checkout at the release commit.", "PostgreSQL migrations pass from an empty database and an upgraded representative database.", "Demo seeding is disabled by default in every production deployment profile.", "JWT issuer, audience, expiry, JWKS rotation and role mapping are tested against the target identity provider.", "Every sensitive endpoint has role and object-ownership tests.", "Upload size, content validation, storage encryption and artifact integrity are tested.", "TLS, HSTS, CSP, trusted proxy and client-IP behavior are verified at the edge.", "Database and artifacts restore together within the declared RPO/RTO.", "Metrics, logs, alerts, rollback and key-compromise runbooks are exercised."]:
    story.append(bullet(text))
story.append(PageBreak())

chapter("REFERENCES")
refs = ["1. Java Platform Documentation, Java 21 LTS.", "2. Spring Boot and Spring Security Reference Documentation.", "3. PostgreSQL Documentation, PostgreSQL 16.", "4. Flyway Database Migration Documentation.", "5. OWASP Application Security Verification Standard and OWASP Top 10.", "6. NIST Digital Signature Standard and RSA-PSS guidance.", "7. Weavechain Bulletproofs implementation and project cryptographic documentation.", "8. Project repository architecture, security, testing and deployment documentation."]
for ref in refs: story.append(P(ref, "BodyLeft"))

doc.build(story)
print(OUT)
