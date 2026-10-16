import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { holderApi } from '../src/api/holderApi';
import { authStore } from '../src/state/authStore';

describe('holderApi Client Service', () => {
  beforeEach(() => {
    authStore.clearSession();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('listCredentials calls GET /api/holder/credentials', async () => {
    const mockList = [
      {
        id: 'hc-uuid-1',
        holderId: 'holder-alice',
        credentialId: 'cred-uuid-1',
        credentialDigestHex: 'abcdef0123456789',
        status: 'ACTIVE',
        version: 1,
        provisionedAt: '2026-10-15T00:00:00Z',
        updatedAt: '2026-10-15T00:00:00Z',
      },
    ];

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockList),
    } as Response);

    const result = await holderApi.listCredentials();

    expect(result).toEqual(mockList);
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [url, options] = fetchSpy.mock.calls[0];
    expect(url).toBe('/api/holder/credentials');
    expect(options?.method).toBe('GET');
  });

  it('getCredentialById calls GET /api/holder/credentials/{id}', async () => {
    const mockItem = {
      id: 'hc-uuid-1',
      holderId: 'holder-alice',
      credentialId: 'cred-uuid-1',
      credentialDigestHex: 'abcdef0123456789',
      status: 'ACTIVE',
      version: 1,
      provisionedAt: '2026-10-15T00:00:00Z',
      updatedAt: '2026-10-15T00:00:00Z',
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockItem),
    } as Response);

    const result = await holderApi.getCredentialById('hc-uuid-1');

    expect(result).toEqual(mockItem);
    const [url, options] = fetchSpy.mock.calls[0];
    expect(url).toBe('/api/holder/credentials/hc-uuid-1');
    expect(options?.method).toBe('GET');
  });

  it('provisionCredential sends POST to /api/holder/credentials/provision', async () => {
    const mockResult = {
      id: 'hc-uuid-2',
      holderId: 'holder-alice',
      credentialId: 'cred-uuid-2',
      credentialDigestHex: '1234567890abcdef',
      status: 'ACTIVE',
      version: 1,
      provisionedAt: '2026-10-15T00:00:00Z',
      updatedAt: '2026-10-15T00:00:00Z',
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 201,
      json: () => Promise.resolve(mockResult),
    } as Response);

    const result = await holderApi.provisionCredential({
      credentialId: 'cred-uuid-2',
    });

    expect(result).toEqual(mockResult);
    const [url, options] = fetchSpy.mock.calls[0];
    expect(url).toBe('/api/holder/credentials/provision');
    expect(options?.method).toBe('POST');
    expect(JSON.parse(options?.body as string)).toEqual({
      credentialId: 'cred-uuid-2',
    });
  });

  it('suspendCredential calls POST /api/holder/credentials/{id}/suspend', async () => {
    const mockResult = {
      id: 'hc-uuid-1',
      holderId: 'holder-alice',
      credentialId: 'cred-uuid-1',
      credentialDigestHex: 'abcdef0123456789',
      status: 'SUSPENDED',
      version: 2,
      provisionedAt: '2026-10-15T00:00:00Z',
      updatedAt: '2026-10-15T00:01:00Z',
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockResult),
    } as Response);

    const result = await holderApi.suspendCredential('hc-uuid-1');

    expect(result).toEqual(mockResult);
    const [url, options] = fetchSpy.mock.calls[0];
    expect(url).toBe('/api/holder/credentials/hc-uuid-1/suspend');
    expect(options?.method).toBe('POST');
  });

  it('activateCredential calls POST /api/holder/credentials/{id}/activate', async () => {
    const mockResult = {
      id: 'hc-uuid-1',
      holderId: 'holder-alice',
      credentialId: 'cred-uuid-1',
      credentialDigestHex: 'abcdef0123456789',
      status: 'ACTIVE',
      version: 3,
      provisionedAt: '2026-10-15T00:00:00Z',
      updatedAt: '2026-10-15T00:02:00Z',
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockResult),
    } as Response);

    const result = await holderApi.activateCredential('hc-uuid-1');

    expect(result).toEqual(mockResult);
    const [url, options] = fetchSpy.mock.calls[0];
    expect(url).toBe('/api/holder/credentials/hc-uuid-1/activate');
    expect(options?.method).toBe('POST');
  });

  it('revokeCredential calls POST /api/holder/credentials/{id}/revoke', async () => {
    const mockResult = {
      id: 'hc-uuid-1',
      holderId: 'holder-alice',
      credentialId: 'cred-uuid-1',
      credentialDigestHex: 'abcdef0123456789',
      status: 'REVOKED',
      version: 4,
      provisionedAt: '2026-10-15T00:00:00Z',
      updatedAt: '2026-10-15T00:03:00Z',
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockResult),
    } as Response);

    const result = await holderApi.revokeCredential('hc-uuid-1');

    expect(result).toEqual(mockResult);
    const [url, options] = fetchSpy.mock.calls[0];
    expect(url).toBe('/api/holder/credentials/hc-uuid-1/revoke');
    expect(options?.method).toBe('POST');
  });

  it('generatePredicateProof sends POST to /api/holder/credentials/proofs/generate', async () => {
    const mockProof = {
      holderCredentialId: 'hc-uuid-1',
      credentialId: 'cred-uuid-1',
      credentialDigestHex: 'abcdef0123456789',
      attributeName: 'age',
      operator: '>=',
      threshold: 18,
      commitmentPointHex: 'pt12345',
      attestationSignatureHex: 'sig67890',
      attestationKeyId: 'default-issuer-key-1',
      nonceHex: '0102030405060708',
      proofVersion: 'V1_BULLETPROOF',
      bitSize: 64,
      proofBytesHex: 'deadbeefcafe123456',
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockProof),
    } as Response);

    const payload = {
      holderCredentialId: 'hc-uuid-1',
      attributeName: 'age',
      operator: '>=',
      threshold: 18,
      nonceHex: '0102030405060708',
    };

    const result = await holderApi.generatePredicateProof(payload);

    expect(result).toEqual(mockProof);
    const [url, options] = fetchSpy.mock.calls[0];
    expect(url).toBe('/api/holder/credentials/proofs/generate');
    expect(options?.method).toBe('POST');
    expect(JSON.parse(options?.body as string)).toEqual(payload);
  });

  it('createCapability sends POST to /api/capabilities', async () => {
    const mockCap = {
      id: 'cap-1',
      capabilityToken: 'tok-abc',
      mode: 'MODE_A_PRESENTED_ARTIFACT' as const,
      protocolVersion: '1.0',
      status: 'ACTIVE',
      expiresAt: '2026-10-16T00:00:00Z',
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 201,
      json: () => Promise.resolve(mockCap),
    } as Response);

    const payload = {
      credentialId: 'cred-1',
      mode: 'MODE_A_PRESENTED_ARTIFACT' as const,
      ttlSeconds: 3600,
    };

    const result = await holderApi.createCapability(payload);

    expect(result).toEqual(mockCap);
    const [url, options] = fetchSpy.mock.calls[0];
    expect(url).toBe('/api/capabilities');
    expect(options?.method).toBe('POST');
    expect(JSON.parse(options?.body as string)).toEqual(payload);
  });

  it('getCapabilityQrBlob calls GET /api/capabilities/{token}/qr and returns Blob', async () => {
    const mockBlob = new Blob(['fake-qr'], { type: 'image/png' });
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'image/png' }),
      blob: () => Promise.resolve(mockBlob),
    } as Response);

    const result = await holderApi.getCapabilityQrBlob('tok-abc');

    expect(result).toBe(mockBlob);
    const [url, options] = fetchSpy.mock.calls[0];
    expect(url).toBe('/api/capabilities/tok-abc/qr');
    expect(options?.method).toBe('GET');
  });

  it('verifyCredential calls GET /api/credentials/{id}/verify', async () => {
    const mockVerification = {
      credentialId: 'cred-1',
      verified: true,
      message: 'Cryptographically valid signature',
      status: 'ACTIVE' as const,
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockVerification),
    } as Response);

    const result = await holderApi.verifyCredential('cred-1');

    expect(result).toEqual(mockVerification);
    const [url, options] = fetchSpy.mock.calls[0];
    expect(url).toBe('/api/credentials/cred-1/verify');
    expect(options?.method).toBe('GET');
  });

  it('getCredentialLifecycleHistory calls GET /api/credentials/{id}/lifecycle/history', async () => {
    const mockHistory = [
      {
        id: 'ev-1',
        credentialId: 'cred-1',
        eventType: 'CREDENTIAL_ISSUED',
        actor: 'issuer-alice',
        actorRole: 'ISSUER',
        fromStatus: 'ACTIVE' as const,
        toStatus: 'ACTIVE' as const,
        timestamp: '2026-10-15T00:00:00Z',
      },
    ];

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockHistory),
    } as Response);

    const result = await holderApi.getCredentialLifecycleHistory('cred-1');

    expect(result).toEqual(mockHistory);
    const [url, options] = fetchSpy.mock.calls[0];
    expect(url).toBe('/api/credentials/cred-1/lifecycle/history');
    expect(options?.method).toBe('GET');
  });
});
