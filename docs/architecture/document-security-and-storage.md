# Phase 10: Document Security & Artifact Storage Architecture

## 1. Overview & Architectural Principle

Phase 10 hardens the document ingestion, content validation, cryptographic hashing, storage abstraction, and retrieval lifecycle in the Zero-Knowledge Document Verification Platform.

The core security principle implemented is:
> **"Document bytes are sensitive artifacts. Cryptographic verification should depend on authenticated metadata and cryptographic digests whenever possible, not on unnecessarily loading or exposing the original document."**

Public verifier endpoints (such as `/api/public/verify/{token}`) and Zero-Knowledge predicate verification evaluate claims strictly over authenticated cryptographic digests, digital signatures, and commitment state without fetching or touching raw artifact bytes.

---

## 2. Implemented Architecture & Security Controls

### 2.1 Content-Type Allowlisting & Magic Byte Inspection
Implemented in [`DocumentContentValidator`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-document/src/main/java/com/zkdoc/document/storage/DocumentContentValidator.java):
- **Magic Number Inspection**: Verifies headers for allowed formats:
  - PDF: `%PDF` (`0x25 0x50 0x44 0x46`)
  - PNG: `\x89PNG\r\n\x1a\n` (`0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A`)
  - JPEG: `\xFF\xD8\xFF` (`0xFF 0xD8 0xFF`)
  - Plain Text: `text/plain` inspected to reject binary executable signatures (`MZ` for PE / Windows executable, `\x7fELF` for Linux binaries) and null bytes.
- **MIME Spoofing Defense**: Rejects files whose content does not match the declared `Content-Type`.
- **File Size Bounding**: Explicit max bound of 25 MB (`MAX_ALLOWED_FILE_SIZE`).
- **Filename Sanitization**:
  - Traversal sequences (`../`, `..\`) stripped.
  - Path components stripped to preserve only base filename.
  - Null bytes (`\0`) and control characters (`\r`, `\n`, `\t`) removed.
  - Truncated to safe length (255 characters).

### 2.2 Storage Abstraction Layer
Implemented via:
- [`DocumentArtifactStorage`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-document/src/main/java/com/zkdoc/document/storage/DocumentArtifactStorage.java): Clean port for streaming persistence, retrieval, and deletion of raw artifact bytes.
- [`InMemoryDocumentArtifactStorage`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-document/src/main/java/com/zkdoc/document/storage/InMemoryDocumentArtifactStorage.java): Thread-safe, non-blocking storage implementation for local and testing environments.
- Ready for cloud backends (S3, MinIO, Azure Blob) without changing core document domain logic.

### 2.3 Streaming SHA-256 Hashing & Storage Integrity
- [`Sha256Hasher`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-document/src/main/java/com/zkdoc/document/Sha256Hasher.java):
  - Added streaming `hash(InputStream is)` to digest large artifacts without heap exhaustion.
- [`DocumentUploadService`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-document/src/main/java/com/zkdoc/document/DocumentUploadService.java):
  - `verifyArtifactStorageIntegrity(String storageKey, byte[] expectedDigest)`: Streams stored artifact from storage and validates cryptographic hash against recorded SHA-256 digest using constant-time comparison (`MessageDigest.isEqual`).
  - `openDocumentArtifactStream(UUID id)`: Returns streaming access to authorized callers.

---

## 3. Verification Decoupling Principle

| Operation | Depends on Raw Document Bytes? | Cryptographic Input |
|---|---|---|
| **Document Upload** | Yes (computes SHA-256 & signs) | Byte Stream |
| **Integrity Audit** | Yes (streams from storage to verify) | Storage Stream vs Stored Hash |
| **Public Token Verification** | **NO** | Public Token & Signature |
| **Credential Issuance** | **NO** | Document Digest & Canonical Payload |
| **ZKP Predicate Verification** | **NO** | Commitment Point & Bulletproof Proof |

---

## 4. Test Verification Suite

The entire test suite passed across all 7 reactor modules:
- [`DocumentSecurityStorageIntegrationTest`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/zkdoc-document/src/test/java/com/zkdoc/document/storage/DocumentSecurityStorageIntegrationTest.java):
  - PDF, PNG, JPEG, Text magic byte verification.
  - Windows PE (`MZ`) and ELF binary polyglot rejection.
  - HTML script injection disguised file rejection.
  - MIME spoofing detection.
  - Path traversal and null byte filename sanitization.
  - Streaming SHA-256 storage integrity check.
  - Post-upload tampering detection.
  - Verification decoupling validation.
- All existing tests in `zkdoc-core-crypto`, `zkdoc-document`, `zkdoc-keys`, `zkdoc-credential`, `zkdoc-zkp`, and `zkdoc-api` pass 100% with 0 failures and 0 errors.
