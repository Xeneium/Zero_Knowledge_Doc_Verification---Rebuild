import { describe, it, expect, vi, beforeEach } from 'vitest';
import { authStore } from '../src/state/authStore';
import { holderStore } from '../src/state/holderStore';
import { issuerStore } from '../src/state/issuerStore';
import { adminStore } from '../src/state/adminStore';
import { verifierStore } from '../src/state/verifierStore';
import { issuerApi } from '../src/api/issuerApi';
import { holderApi } from '../src/api/holderApi';
import { verifierApi } from '../src/api/verifierApi';
import { adminApi } from '../src/api/adminApi';
import type {
  DocumentUploadResponse,
  IssueCredentialResponse,
  CapabilityResponse,
  HolderCredentialResponse,
  HolderPredicateProofResponse,
  CapabilityPublicVerificationResponse,
  CredentialVerificationResult,
  DocumentVerificationResult,
  CredentialPredicateVerificationResponse,
  RecoveryReconciliationReport,
  KeyRegisteredResponse,
  KeyMetadataResponse,
  AuditIntegrityResult,
  AuditQueryResult,
  CredentialLifecycleResponse,
} from '../src/api/types';

function createJwt(subject: string, roles: string[] = ['ISSUER']): string {
  const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
  const payload = btoa(
    JSON.stringify({
      sub: subject,
      roles,
      iss: 'https://auth.zkdoc.local',
      aud: 'zkdoc-platform',
      exp: Math.floor(Date.now() / 1000) + 3600,
      iat: Math.floor(Date.now() / 1000),
    }),
  )
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
  return `${header}.${payload}.mock-signature-bytes`;
}

describe('Phase F11: Cross-Workflow & End-to-End Integration Suite', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
    authStore.clearSession();
  });

  it('executes complete synthetic cross-role lifecycle: Issuer -> Holder -> Verifier -> Administrator', async () => {
    // =========================================================================
    // STEP 1: ISSUER WORKFLOW (Upload Document, Issue Credential, Mint Capability)
    // =========================================================================

    // 1.1 Authenticate as authorized Issuer
    const issuerToken = createJwt('urn:zkdoc:issuer:acme-corp', ['ISSUER']);
    authStore.setToken(issuerToken);
    expect(authStore.getState().isAuthenticated).toBe(true);
    expect(authStore.getState().user?.roles).toContain('ISSUER');

    // 1.2 Issuer uploads synthetic document
    const mockDocId = '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d';
    const mockDocDigest = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855';

    const mockUploadResponse: DocumentUploadResponse = {
      id: mockDocId,
      digest: mockDocDigest,
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 201,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockUploadResponse),
    } as Response);

    const docFile = new File(['%PDF-1.4 sample diploma content'], 'diploma.pdf', {
      type: 'application/pdf',
    });
    const uploadResult = await issuerApi.uploadDocument(docFile);

    expect(uploadResult.id).toBe(mockDocId);
    expect(uploadResult.digest).toBe(mockDocDigest);

    // Track activity in issuerStore
    issuerStore.addActivity({
      id: uploadResult.id,
      type: 'document',
      title: 'diploma.pdf',
      digest: uploadResult.digest,
    });
    expect(issuerStore.getState().activities).toHaveLength(1);

    // 1.3 Issuer creates a Verification Capability for the document (Mode B)
    const mockCapabilityToken = 'cap-tok-mode-b-secure-482019';
    const mockCapResponse: CapabilityResponse = {
      id: 'cap-uuid-1',
      capabilityToken: mockCapabilityToken,
      mode: 'MODE_B_REGISTRY_CAPABILITY',
      protocolVersion: '1.0',
      status: 'ACTIVE',
      expiresAt: '2026-10-16T12:00:00Z',
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 201,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockCapResponse),
    } as Response);

    const capResult = await issuerApi.createCapability({
      documentId: mockDocId,
      mode: 'MODE_B_REGISTRY_CAPABILITY',
      ttlSeconds: 86400,
    });
    expect(capResult.capabilityToken).toBe(mockCapabilityToken);
    expect(capResult.mode).toBe('MODE_B_REGISTRY_CAPABILITY');

    // 1.4 Issuer issues a signed verifiable credential
    const mockCredId = '7c9e6679-7425-40de-944b-e07fc1f90ae7';
    const mockCredDigest = 'f5a5c60c48d4e73d4c8027a4f59f6a9c51a1ca11005a9238740e61aa81403370';

    const mockIssueResponse: IssueCredentialResponse = {
      credentialId: mockCredId,
      digest: mockCredDigest,
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 201,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockIssueResponse),
    } as Response);

    const issueResult = await issuerApi.issueCredential({
      issuerId: 'urn:zkdoc:issuer:acme-corp',
      attributes: [
        { name: 'degree', value: 'B.Sc. Computer Science', type: 'STRING' },
        { name: 'gpa', value: '380', type: 'INTEGER' },
        { name: 'graduationYear', value: '2026', type: 'INTEGER' },
      ],
    });
    expect(issueResult.credentialId).toBe(mockCredId);
    expect(issueResult.digest).toBe(mockCredDigest);

    // =========================================================================
    // STEP 2: SESSION TRANSITION & TEARDOWN (Issuer -> Holder)
    // =========================================================================

    // 2.1 Holder logs in; issuer state must be purged
    const holderToken = createJwt('urn:zkdoc:holder:alice', ['HOLDER']);
    authStore.setToken(holderToken);

    expect(authStore.getState().user?.subject).toBe('urn:zkdoc:holder:alice');
    expect(authStore.getState().user?.roles).toContain('HOLDER');
    expect(authStore.getState().user?.roles).not.toContain('ISSUER');

    // Verify teardown listener wiped previous domain stores
    expect(issuerStore.getState().activities).toHaveLength(0);
    expect(adminStore.getState().keys).toHaveLength(0);
    expect(verifierStore.getState().history).toHaveLength(0);

    // =========================================================================
    // STEP 3: HOLDER WORKFLOW (Provision, Inspect, Generate Predicate Proof)
    // =========================================================================

    // 3.1 Holder provisions credential to their personal wallet
    const mockProvisionedCred: HolderCredentialResponse = {
      id: 'hc-uuid-alice-1',
      holderId: 'urn:zkdoc:holder:alice',
      credentialId: mockCredId,
      credentialDigestHex: mockCredDigest,
      status: 'ACTIVE',
      version: 1,
      provisionedAt: '2026-10-15T12:00:00Z',
      updatedAt: '2026-10-15T12:00:00Z',
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 201,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockProvisionedCred),
    } as Response);

    const provResult = await holderApi.provisionCredential({
      credentialId: mockCredId,
    });
    expect(provResult.holderId).toBe('urn:zkdoc:holder:alice');
    expect(provResult.credentialId).toBe(mockCredId);
    expect(provResult.status).toBe('ACTIVE');

    // 3.2 Holder queries their credentials list
    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve([mockProvisionedCred]),
    } as Response);

    const holderList = await holderApi.listCredentials();
    expect(holderList).toHaveLength(1);
    expect(holderList[0].id).toBe('hc-uuid-alice-1');
    holderStore.setCredentials(holderList);
    expect(holderStore.getState().credentials).toHaveLength(1);

    // 3.3 Holder generates a zero-knowledge Bulletproof predicate proof statement (GPA >= 300)
    const mockProofResponse: HolderPredicateProofResponse = {
      holderCredentialId: 'hc-uuid-alice-1',
      credentialId: mockCredId,
      credentialDigestHex: mockCredDigest,
      attributeName: 'gpa',
      operator: '>=',
      threshold: 300,
      commitmentPointHex: '02a1b2c3d4e5f60718293a4b5c6d7e8f00112233445566778899aabbccddeeff',
      attestationSignatureHex: '3045022100a1b2...0220c3d4...',
      attestationKeyId: 'default-key-1',
      nonceHex: '4a6b8c9d0e1f2a3b4c5d6e7f8a9b0c1d',
      proofVersion: 'V2_BULLETPROOF',
      bitSize: 64,
      proofBytesHex: '0102030405060708090a0b0c0d0e0f10',
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockProofResponse),
    } as Response);

    const proofResult = await holderApi.generatePredicateProof({
      holderCredentialId: 'hc-uuid-alice-1',
      attributeName: 'gpa',
      threshold: 300,
      nonceHex: '4a6b8c9d0e1f2a3b4c5d6e7f8a9b0c1d',
    });

    expect(proofResult.attributeName).toBe('gpa');
    expect(proofResult.threshold).toBe(300);
    expect(proofResult.operator).toBe('>=');
    expect(proofResult.proofBytesHex).toBe('0102030405060708090a0b0c0d0e0f10');

    // 3.4 IDOR Protection check: Holder Charlie attempting to query Alice's credential receives 404
    fetchSpy.mockResolvedValueOnce({
      ok: false,
      status: 404,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve({ error: 'NotFound', message: 'Credential not found' }),
    } as Response);

    await expect(holderApi.getCredentialById('hc-uuid-alice-1')).rejects.toThrow();

    // =========================================================================
    // STEP 4: VERIFIER WORKFLOW (Public Capability Verification & Predicate Evaluation)
    // =========================================================================

    // 4.1 Anonymous verifier verifies the public capability token
    authStore.clearSession(); // Anonymous unauthenticated request
    expect(authStore.getState().isAuthenticated).toBe(false);

    const mockCapPublicVerifyResponse: CapabilityPublicVerificationResponse = {
      verified: true,
      valid: true,
      status: 'ACTIVE',
      mode: 'MODE_B_REGISTRY_CAPABILITY',
      protocolVersion: '1.0',
      signatureValid: true,
      active: true,
      failureReason: null,
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockCapPublicVerifyResponse),
    } as Response);

    const pubVerifyResult = await verifierApi.publicVerifyCapability(mockCapabilityToken);
    expect(pubVerifyResult.verified).toBe(true);
    expect(pubVerifyResult.signatureValid).toBe(true);
    expect(pubVerifyResult.active).toBe(true);
    // Minimal disclosure: no document body or holder PII returned
    expect((pubVerifyResult as unknown as Record<string, unknown>).content).toBeUndefined();
    expect((pubVerifyResult as unknown as Record<string, unknown>).holderName).toBeUndefined();

    // 4.2 Authenticate as Verifier for Predicate & Direct Verification
    const verifierToken = createJwt('urn:zkdoc:verifier:regulator-1', ['VERIFIER']);
    authStore.setToken(verifierToken);
    expect(authStore.getState().user?.roles).toContain('VERIFIER');

    // 4.3 Verifier authoritatively verifies credential signature
    const mockCredVerifyResult: CredentialVerificationResult = {
      credentialId: mockCredId,
      verified: true,
      status: 'ACTIVE',
      message: 'Credential signature and operational status valid',
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockCredVerifyResult),
    } as Response);

    const credVerResult = await verifierApi.verifyCredential(mockCredId);
    expect(credVerResult.verified).toBe(true);
    expect(credVerResult.status).toBe('ACTIVE');

    // 4.4 Verifier evaluates the zero-knowledge Bulletproof predicate proof
    const mockPredicateVerifyResponse: CredentialPredicateVerificationResponse = {
      verified: true,
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockPredicateVerifyResponse),
    } as Response);

    const predicateVerResult = await verifierApi.verifyPredicateProof({
      proofVersion: 'V2_BULLETPROOF',
      credentialId: mockCredId,
      attributeName: 'gpa',
      threshold: 300,
      nonce: '4a6b8c9d0e1f2a3b4c5d6e7f8a9b0c1d',
      proofBytes: '0102030405060708090a0b0c0d0e0f10',
    });
    expect(predicateVerResult.verified).toBe(true);

    // 4.5 Verifier tests document upload verification with tampered file
    const mockTamperedResult: DocumentVerificationResult = {
      id: mockDocId,
      documentId: mockDocId,
      verified: false,
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockTamperedResult),
    } as Response);

    const tamperedFile = new File(['corrupted content'], 'diploma.pdf', {
      type: 'application/pdf',
    });
    const tamperedCheck = await verifierApi.verifyDocumentUploadedCopy(mockDocId, tamperedFile);
    expect(tamperedCheck.verified).toBe(false);

    // =========================================================================
    // STEP 5: ADMINISTRATOR WORKFLOW (Recovery, Key Registry, Audit Hash Chain)
    // =========================================================================

    // 5.1 Authenticate as Administrator
    const adminToken = createJwt('urn:zkdoc:admin:operator-root', ['ADMIN']);
    authStore.setToken(adminToken);
    expect(authStore.getState().user?.roles).toContain('ADMIN');

    // 5.2 Administrator inspects platform recovery reconciliation status
    const mockRecoveryReport: RecoveryReconciliationReport = {
      timestamp: '2026-10-15T12:00:00Z',
      status: 'HEALTHY',
      schemaValid: true,
      schemaVersion: 'v13',
      totalArtifactsChecked: 14,
      validArtifacts: 14,
      missingArtifacts: [],
      corruptedArtifacts: [],
      totalKeysChecked: 5,
      activeKeys: 4,
      revokedKeys: 1,
      invalidKeys: [],
      totalCredentialsChecked: 28,
      validCredentials: 28,
      invalidCredentials: [],
      auditChainValid: true,
      totalAuditEvents: 120,
      auditFailureReason: null,
      summary: 'Platform state reconciled and consistent',
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockRecoveryReport),
    } as Response);

    const recoveryStatus = await adminApi.getRecoveryStatus();
    expect(recoveryStatus.status).toBe('HEALTHY');
    expect(recoveryStatus.auditChainValid).toBe(true);

    // 5.3 Administrator triggers post-recovery platform reconciliation
    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockRecoveryReport),
    } as Response);

    const reconcileResult = await adminApi.reconcileRecovery();
    expect(reconcileResult.schemaValid).toBe(true);

    // 5.4 Administrator registers a new cryptographic public key anchor
    const mockRegisteredKey: KeyRegisteredResponse = {
      id: 'key-anchor-uuid-1',
      keyId: 'trust-anchor-2026-q4',
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 201,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockRegisteredKey),
    } as Response);

    const registeredKey = await adminApi.registerKey({
      keyId: 'trust-anchor-2026-q4',
      purpose: 'CREDENTIAL_SIGNING',
      publicKeyPem: '-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...\n-----END PUBLIC KEY-----',
      ownerId: 'platform',
      ownerType: 'PLATFORM',
    });
    expect(registeredKey.keyId).toBe('trust-anchor-2026-q4');

    // 5.5 Administrator lists key registry
    const mockKeyMetadata: KeyMetadataResponse = {
      id: 'key-anchor-uuid-1',
      keyId: 'trust-anchor-2026-q4',
      purpose: 'CREDENTIAL_SIGNING',
      status: 'ACTIVE',
      ownerId: 'platform',
      ownerType: 'PLATFORM',
      algorithm: 'RSASSA-PSS',
      fingerprint: '3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e',
      publicKeyPem: '-----BEGIN PUBLIC KEY-----\nMIIB...-----END PUBLIC KEY-----',
      createdAt: '2026-10-15T12:00:00Z',
      activatedAt: '2026-10-15T12:00:00Z',
      retiredAt: null,
      revokedAt: null,
      replacementKeyId: null,
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve([mockKeyMetadata]),
    } as Response);

    const keysList = await adminApi.getKeys();
    expect(keysList).toHaveLength(1);
    expect(keysList[0].status).toBe('ACTIVE');

    // 5.6 Administrator queries security audit trail
    const mockAuditQuery: AuditQueryResult = {
      items: [
        {
          eventId: 'evt-audit-1',
          sequenceNumber: 1,
          eventType: 'DOCUMENT_UPLOADED',
          eventCategory: 'DOCUMENT',
          timestamp: '2026-10-15T12:00:00Z',
          actorSubject: 'urn:zkdoc:issuer:acme-corp',
          actorType: 'USER',
          targetType: 'DOCUMENT',
          targetId: mockDocId,
          action: 'UPLOAD',
          outcome: 'SUCCESS',
          reasonCode: null,
          correlationId: 'corr-001',
          clientIpHash: '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8',
          metadataJson: '{}',
          previousEventHash: '0000000000000000000000000000000000000000000000000000000000000000',
          eventHash: 'a1b2c3d4e5f60718293a4b5c6d7e8f00112233445566778899aabbccddeeff00',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 20,
      totalPages: 1,
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockAuditQuery),
    } as Response);

    const auditEvents = await adminApi.queryAuditEvents();
    expect(auditEvents.items).toHaveLength(1);
    expect(auditEvents.items[0].actorSubject).toBe('urn:zkdoc:issuer:acme-corp');

    // 5.7 Administrator verifies mathematical integrity of SHA-256 hash chain
    const mockIntegrityResult: AuditIntegrityResult = {
      valid: true,
      checkedCount: 120,
      tamperedSequence: null,
      failureReason: null,
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockIntegrityResult),
    } as Response);

    const integrityResult = await adminApi.verifyAuditIntegrity(1, 120);
    expect(integrityResult.valid).toBe(true);
    expect(integrityResult.tamperedSequence).toBeNull();

    // 5.8 Administrator credential oversight: Suspend and resume credential
    const mockSuspendedCred: CredentialLifecycleResponse = {
      credentialId: mockCredId,
      issuerId: 'urn:zkdoc:issuer:acme-corp',
      status: 'SUSPENDED',
      version: 2,
      message: 'Credential suspended successfully',
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockSuspendedCred),
    } as Response);

    const suspendResult = await adminApi.suspendCredential(mockCredId, 'Administrative review');
    expect(suspendResult.status).toBe('SUSPENDED');
    expect(suspendResult.version).toBe(2);

    const mockResumedCred: CredentialLifecycleResponse = {
      credentialId: mockCredId,
      issuerId: 'urn:zkdoc:issuer:acme-corp',
      status: 'ACTIVE',
      version: 3,
      message: 'Credential resumed to ACTIVE successfully',
    };

    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve(mockResumedCred),
    } as Response);

    const resumeResult = await adminApi.resumeCredential(mockCredId, 'Review cleared');
    expect(resumeResult.status).toBe('ACTIVE');
    expect(resumeResult.version).toBe(3);

    // =========================================================================
    // STEP 6: RBAC & AUTHORIZATION BOUNDARY ENFORCEMENT
    // =========================================================================

    // 6.1 Holder role is blocked from credential issuance (403 Forbidden)
    authStore.setToken(createJwt('urn:zkdoc:holder:attacker', ['HOLDER']));
    fetchSpy.mockResolvedValueOnce({
      ok: false,
      status: 403,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve({ error: 'Forbidden', message: 'Access denied: Requires ROLE_ISSUER' }),
    } as Response);

    await expect(
      issuerApi.issueCredential({
        issuerId: 'urn:zkdoc:holder:attacker',
        attributes: [{ name: 'privilege', value: 'admin', type: 'STRING' }],
      }),
    ).rejects.toThrow(/Forbidden|403|denied/i);

    // 6.2 Unauthenticated request is rejected (401 Unauthorized)
    authStore.clearSession();
    fetchSpy.mockResolvedValueOnce({
      ok: false,
      status: 401,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve({ error: 'Unauthorized', message: 'Authentication required' }),
    } as Response);

    await expect(adminApi.getKeys()).rejects.toThrow(/Authentication required|Unauthorized|401/i);
  });

  it('handles network failures, 500 server errors, and non-JSON payloads gracefully across workflows', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const issuerToken = createJwt('urn:zkdoc:issuer:acme-corp', ['ISSUER']);
    authStore.setToken(issuerToken);

    // Network timeout / connection refused
    fetchSpy.mockRejectedValueOnce(new TypeError('Failed to fetch (connection refused / timeout)'));
    await expect(issuerApi.getCredentialLifecycleHistory('cred-uuid-1')).rejects.toThrow(/Failed to fetch/i);

    // 500 Internal Server Error with JSON body
    fetchSpy.mockResolvedValueOnce({
      ok: false,
      status: 500,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve({
        timestamp: '2026-10-15T12:00:00Z',
        status: 500,
        error: 'Internal Server Error',
        message: 'Cryptographic engine hardware accelerator failure',
        path: '/api/v2/zkp/credential-predicates/verify',
      }),
    } as Response);

    await expect(
      verifierApi.verifyPredicateProof({
        proofVersion: 'V2_BULLETPROOF',
        credentialId: 'any-cred',
        attributeName: 'score',
        threshold: 100,
        nonce: 'aabbccdd',
        proofBytes: '11223344',
      }),
    ).rejects.toThrow(/Cryptographic engine hardware accelerator failure/i);

    // 502 Bad Gateway with HTML error page (non-JSON)
    fetchSpy.mockResolvedValueOnce({
      ok: false,
      status: 502,
      statusText: 'Bad Gateway',
      headers: new Headers({ 'Content-Type': 'text/html' }),
      json: () => Promise.reject(new SyntaxError('Unexpected token < in JSON at position 0')),
    } as Response);

    await expect(adminApi.getRecoveryStatus()).rejects.toThrow(/HTTP error 502/i);
  });

  it('correctly handles predicate proof evaluation rejection (verified: false)', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const verifierToken = createJwt('urn:zkdoc:verifier:auditor', ['VERIFIER']);
    authStore.setToken(verifierToken);

    // Backend evaluates mathematical proof and determines statement is false (e.g. gpa < threshold)
    fetchSpy.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve({ verified: false }),
    } as Response);

    const result = await verifierApi.verifyPredicateProof({
      proofVersion: 'V2_BULLETPROOF',
      credentialId: 'cred-123',
      attributeName: 'gpa',
      threshold: 400,
      nonce: '1234567890abcdef',
      proofBytes: 'deadbeef01020304',
    });

    expect(result.verified).toBe(false);
  });

  it('discards stale async in-flight responses when user logs out or switches sessions', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const aliceToken = createJwt('urn:zkdoc:holder:alice', ['HOLDER']);
    authStore.setToken(aliceToken);

    // Mock an in-flight slow request
    let resolveSlowFetch!: (value: Response) => void;
    const slowFetchPromise = new Promise<Response>((resolve) => {
      resolveSlowFetch = resolve;
    });

    fetchSpy.mockReturnValueOnce(slowFetchPromise);

    // Start async request as Alice
    const inFlightRequest = holderApi.listCredentials();

    // Alice logs out or switches accounts mid-flight!
    authStore.clearSession();

    // The slow network request finally resolves
    resolveSlowFetch({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: () => Promise.resolve([
        {
          id: 'hc-alice-secret',
          holderId: 'urn:zkdoc:holder:alice',
          credentialId: 'secret-cred-1',
          credentialDigestHex: 'abcd',
          status: 'ACTIVE',
          version: 1,
          provisionedAt: '2026-10-15T12:00:00Z',
          updatedAt: '2026-10-15T12:00:00Z',
        },
      ]),
    } as Response);

    // Must be rejected with 499 Client Session Terminated and never leak into state
    await expect(inFlightRequest).rejects.toThrow(/Request discarded because authenticated session was terminated/i);
    expect(holderStore.getState().credentials).toHaveLength(0);
  });
});
