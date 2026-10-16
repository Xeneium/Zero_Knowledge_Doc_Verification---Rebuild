import { describe, it, expect, vi, beforeEach } from 'vitest';
import { adminApi } from '../src/api/adminApi';
import { authStore } from '../src/state/authStore';
import type {
  KeyRegistrationRequest,
  KeyStatusChangeRequest,
  KeyRotateRequest,
  AuditQueryFilterParams,
  CredentialPredicateVerificationRequest,
} from '../src/api/types';

describe('adminApi Service Layer', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    authStore.clearSession();
  });

  const setAuthToken = () => {
    const payload = btoa(
      JSON.stringify({
        sub: 'urn:zkdoc:admin:operator1',
        roles: ['ADMIN'],
        iss: 'https://auth.zkdoc.internal',
        aud: 'zkdoc-api',
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    authStore.setToken(`header.${payload}.sig`);
  };

  describe('Key Registry Admin Endpoints', () => {
    it('getKeys calls GET /api/admin/keys with auth header', async () => {
      setAuthToken();
      const mockKeys = [{ keyId: 'key-1', status: 'ACTIVE' }];
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockKeys),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.getKeys();

      expect(result).toEqual(mockKeys);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/admin/keys');
      expect(options?.method).toBe('GET');
      expect((options?.headers as Headers).get('Authorization')).toContain('Bearer header.');
    });

    it('getKey calls GET /api/admin/keys/{keyId}', async () => {
      setAuthToken();
      const mockKey = { keyId: 'key-99', status: 'ACTIVE' };
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockKey),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.getKey('key-99');

      expect(result).toEqual(mockKey);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/admin/keys/key-99');
      expect(options?.method).toBe('GET');
    });

    it('registerKey calls POST /api/admin/keys with JSON body', async () => {
      setAuthToken();
      const req: KeyRegistrationRequest = {
        keyId: 'issuer-new-key',
        publicKeyPem: '-----BEGIN PUBLIC KEY-----\nMIIB...',
        algorithm: 'RSA-PSS',
        purpose: 'CREDENTIAL_SIGNING',
        ownerType: 'ISSUER',
        ownerId: 'issuer-corp',
      };
      const mockRes = {
        id: 'key-uuid-1',
        keyId: 'issuer-new-key',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 201,
        json: () => Promise.resolve(mockRes),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.registerKey(req);

      expect(result).toEqual(mockRes);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/admin/keys');
      expect(options?.method).toBe('POST');
      expect(options?.body).toBe(JSON.stringify(req));
    });

    it('changeKeyStatus calls PATCH /api/admin/keys/{keyId}/status', async () => {
      setAuthToken();
      const req: KeyStatusChangeRequest = {
        status: 'RETIRING',
        reason: 'Scheduled rotation',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 204,
        json: () => Promise.resolve(),
        headers: new Headers(),
      } as unknown as Response);

      await adminApi.changeKeyStatus('key-1', req);

      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/admin/keys/key-1/status');
      expect(options?.method).toBe('PATCH');
      expect(options?.body).toBe(JSON.stringify(req));
    });

    it('rotateKey calls POST /api/admin/keys/{keyId}/rotate', async () => {
      setAuthToken();
      const req: KeyRotateRequest = {
        replacementKeyId: 'key-2',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 204,
        json: () => Promise.resolve(),
        headers: new Headers(),
      } as unknown as Response);

      await adminApi.rotateKey('key-1', req);

      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/admin/keys/key-1/rotate');
      expect(options?.method).toBe('POST');
      expect(options?.body).toBe(JSON.stringify(req));
    });
  });

  describe('Recovery Admin Endpoints', () => {
    it('getRecoveryStatus calls GET /api/admin/recovery/status', async () => {
      setAuthToken();
      const mockReport = { status: 'HEALTHY', schemaValid: true };
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockReport),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.getRecoveryStatus();

      expect(result).toEqual(mockReport);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/admin/recovery/status');
      expect(options?.method).toBe('GET');
    });

    it('reconcileRecovery calls POST /api/admin/recovery/reconcile', async () => {
      setAuthToken();
      const mockReport = { status: 'HEALTHY', summary: 'Reconciled' };
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockReport),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.reconcileRecovery();

      expect(result).toEqual(mockReport);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/admin/recovery/reconcile');
      expect(options?.method).toBe('POST');
    });
  });

  describe('Security Audit Endpoints', () => {
    it('queryAuditEvents calls GET /api/admin/audit/events with search parameters', async () => {
      setAuthToken();
      const filter: AuditQueryFilterParams = {
        category: 'AUTHENTICATION',
        actor: 'alice',
        type: 'USER_LOGIN',
        page: 1,
        size: 25,
      };
      const mockResult = { items: [], page: 1, size: 25, totalElements: 0, totalPages: 0 };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockResult),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.queryAuditEvents(filter);

      expect(result).toEqual(mockResult);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/admin/audit/events?category=AUTHENTICATION&type=USER_LOGIN&actor=alice&page=1&size=25');
      expect(options?.method).toBe('GET');
    });

    it('verifyAuditIntegrity calls GET /api/admin/audit/verify-integrity with sequence bounds', async () => {
      setAuthToken();
      const mockIntegrity = { valid: true, checkedCount: 50, tamperedSequence: null, failureReason: null };
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockIntegrity),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.verifyAuditIntegrity(1, 50);

      expect(result).toEqual(mockIntegrity);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/admin/audit/verify-integrity?startSeq=1&endSeq=50');
      expect(options?.method).toBe('GET');
    });
  });

  describe('Credential Oversight Endpoints', () => {
    it('getCredentialLifecycle calls GET /api/credentials/{id}/lifecycle', async () => {
      setAuthToken();
      const mockState = { credentialId: 'c-1', state: 'ACTIVE' };
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockState),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.getCredentialLifecycle('c-1');

      expect(result).toEqual(mockState);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/credentials/c-1/lifecycle');
      expect(options?.method).toBe('GET');
    });

    it('getCredentialLifecycleHistory calls GET /api/credentials/{id}/lifecycle/history', async () => {
      setAuthToken();
      const mockHistory = [{ state: 'ISSUED', reason: 'Created', actor: 'issuer', timestamp: '2026-01-01' }];
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockHistory),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.getCredentialLifecycleHistory('c-1');

      expect(result).toEqual(mockHistory);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/credentials/c-1/lifecycle/history');
      expect(options?.method).toBe('GET');
    });

    it('verifyCredential calls GET /api/credentials/{id}/verify', async () => {
      setAuthToken();
      const mockVerify = { valid: true, signatureValid: true };
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockVerify),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.verifyCredential('c-1');

      expect(result).toEqual(mockVerify);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/credentials/c-1/verify');
      expect(options?.method).toBe('GET');
    });

    it('suspendCredential calls POST /api/credentials/{id}/suspend with reason query param', async () => {
      setAuthToken();
      const mockState = { credentialId: 'c-1', state: 'SUSPENDED' };
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockState),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.suspendCredential('c-1', 'Audit investigation');

      expect(result).toEqual(mockState);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/credentials/c-1/suspend');
      expect(options?.method).toBe('POST');
      expect(options?.body).toBe(JSON.stringify({ reason: 'Audit investigation' }));
    });

    it('resumeCredential calls POST /api/credentials/{id}/resume with reason query param', async () => {
      setAuthToken();
      const mockState = { credentialId: 'c-1', state: 'ACTIVE' };
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockState),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.resumeCredential('c-1', 'Cleared of suspicion');

      expect(result).toEqual(mockState);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/credentials/c-1/resume');
      expect(options?.method).toBe('POST');
      expect(options?.body).toBe(JSON.stringify({ reason: 'Cleared of suspicion' }));
    });

    it('revokeCredential calls POST /api/credentials/{id}/revoke with reason query param', async () => {
      setAuthToken();
      const mockState = { credentialId: 'c-1', state: 'REVOKED' };
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockState),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.revokeCredential('c-1', 'Compromised private key');

      expect(result).toEqual(mockState);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/credentials/c-1/revoke');
      expect(options?.method).toBe('POST');
      expect(options?.body).toBe(JSON.stringify({ reason: 'Compromised private key' }));
    });
  });

  describe('Predicate Proof Verification Endpoint', () => {
    it('verifyCredentialPredicate calls POST /api/v2/zkp/credential-predicates/verify', async () => {
      setAuthToken();
      const req: CredentialPredicateVerificationRequest = {
        proofVersion: 'BP_V1',
        credentialId: 'c-1',
        attributeName: 'age',
        threshold: 21,
        nonce: 'bm9uY2UtMTIz',
        proofBytes: 'cHJvb2YtYnl0ZXM=',
      };
      const mockRes = { verified: true, satisfied: true };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockRes),
        headers: new Headers(),
      } as unknown as Response);

      const result = await adminApi.verifyCredentialPredicate(req);

      expect(result).toEqual(mockRes);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/v2/zkp/credential-predicates/verify');
      expect(options?.method).toBe('POST');
      expect(options?.body).toBe(JSON.stringify(req));
    });
  });

  describe('Error Handling', () => {
    it('throws ApiError with server message when response is not ok', async () => {
      setAuthToken();
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 403,
        json: () => Promise.resolve({ message: 'Access denied: Requires ROLE_ADMIN' }),
        headers: new Headers(),
      } as unknown as Response);

      await expect(adminApi.getKeys()).rejects.toThrow('Access denied: Requires ROLE_ADMIN');
    });

    it('falls back to status code text when json parsing fails on error', async () => {
      setAuthToken();
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        json: () => Promise.reject(new Error('Invalid JSON')),
        headers: new Headers(),
      } as unknown as Response);

      await expect(adminApi.getKeys()).rejects.toThrow('HTTP error 500');
    });
  });
});
