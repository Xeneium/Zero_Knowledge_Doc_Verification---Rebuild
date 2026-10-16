import { describe, it, expect, vi, beforeEach } from 'vitest';
import { verifierApi } from '../src/api/verifierApi';

describe('verifierApi Service Layer', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('publicVerifyToken calls GET /api/public/verify/{token}', async () => {
    const mockResponse = { signatureValid: true, documentActive: true };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockResponse),
      headers: new Headers(),
    } as unknown as Response);

    const result = await verifierApi.publicVerifyToken('test-token-123');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/public/verify/test-token-123',
      expect.objectContaining({
        method: 'GET',
      }),
    );
    expect(result).toEqual(mockResponse);
  });

  it('publicVerifyCapability calls GET /api/public/verify/capability/{token}', async () => {
    const mockResponse = {
      verified: true,
      valid: true,
      status: 'VALID',
      mode: 'MODE_B_REGISTRY_CAPABILITY',
      protocolVersion: 'QR_V1',
      signatureValid: true,
      active: true,
      failureReason: null,
    };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockResponse),
      headers: new Headers(),
    } as unknown as Response);

    const result = await verifierApi.publicVerifyCapability('cap-token-456');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/public/verify/capability/cap-token-456',
      expect.objectContaining({
        method: 'GET',
      }),
    );
    expect(result).toEqual(mockResponse);
  });

  it('verifyDocumentUploadedCopy calls POST /api/documents/{id}/verify-upload with FormData', async () => {
    const mockResponse = {
      documentId: 'doc-uuid-123',
      verified: true,
    };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockResponse),
      headers: new Headers(),
    } as unknown as Response);

    const testFile = new File(['document content'], 'test.pdf', {
      type: 'application/pdf',
    });
    const result = await verifierApi.verifyDocumentUploadedCopy(
      'doc-uuid-123',
      testFile,
    );

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/documents/doc-uuid-123/verify-upload',
      expect.objectContaining({
        method: 'POST',
        body: expect.any(FormData),
      }),
    );
    expect(result).toEqual(mockResponse);
  });

  it('verifyDocumentStoredCopy calls GET /api/documents/{id}/verify', async () => {
    const mockResponse = {
      documentId: 'doc-uuid-123',
      verified: true,
    };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockResponse),
      headers: new Headers(),
    } as unknown as Response);

    const result = await verifierApi.verifyDocumentStoredCopy('doc-uuid-123');

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/documents/doc-uuid-123/verify',
      expect.objectContaining({
        method: 'GET',
      }),
    );
    expect(result).toEqual(mockResponse);
  });

  it('verifyCredential calls GET /api/credentials/{id}/verify without and with asOf', async () => {
    const mockResponse = {
      credentialId: 'cred-uuid-123',
      verified: true,
      digestMatches: true,
      signatureValid: true,
      issuerActive: true,
      keyTrusted: true,
      operationalStatusValid: true,
      failureReason: null,
    };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockResponse),
      headers: new Headers(),
    } as unknown as Response);

    const res1 = await verifierApi.verifyCredential('cred-uuid-123');
    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/credentials/cred-uuid-123/verify',
      expect.anything(),
    );
    expect(res1).toEqual(mockResponse);

    const asOfTime = '2026-10-15T00:00:00Z';
    const res2 = await verifierApi.verifyCredential('cred-uuid-123', asOfTime);
    expect(fetchSpy).toHaveBeenCalledWith(
      `/api/credentials/cred-uuid-123/verify?asOf=${encodeURIComponent(asOfTime)}`,
      expect.anything(),
    );
    expect(res2).toEqual(mockResponse);
  });

  it('getCredentialLifecycle calls GET /api/credentials/{id}/lifecycle', async () => {
    const mockResponse = {
      credentialId: 'cred-uuid-123',
      status: 'ACTIVE',
      version: 1,
      issuedAt: '2026-10-14T00:00:00Z',
      expiresAt: '2027-10-14T00:00:00Z',
      message: 'Active',
    };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockResponse),
      headers: new Headers(),
    } as unknown as Response);

    const result = await verifierApi.getCredentialLifecycle('cred-uuid-123');
    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/credentials/cred-uuid-123/lifecycle',
      expect.anything(),
    );
    expect(result).toEqual(mockResponse);
  });

  it('verifyPredicateProof calls POST /api/v2/zkp/credential-predicates/verify', async () => {
    const mockResponse = {
      verified: true,
    };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockResponse),
      headers: new Headers(),
    } as unknown as Response);

    const payload = {
      proofVersion: 'BULLETPROOF_V1',
      credentialId: 'cred-uuid-123',
      attributeName: 'income',
      threshold: 50000,
      nonce: 'bm9uY2UtMTIzNA==',
      proofBytes: 'cHJvb2YtYnl0ZXMtMTIzNA==',
    };

    const result = await verifierApi.verifyPredicateProof(payload);
    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/v2/zkp/credential-predicates/verify',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    );
    expect(result).toEqual(mockResponse);
  });
});
