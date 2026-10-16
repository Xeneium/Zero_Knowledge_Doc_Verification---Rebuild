import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { issuerApi } from '../src/api/issuerApi';
import { authStore } from '../src/state/authStore';

describe('issuerApi Client Integration Service', () => {
  beforeEach(() => {
    authStore.clearSession();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Document Operations', () => {
    it('uploadDocument sends multipart request to /api/documents', async () => {
      const mockResult = {
        id: 'doc-uuid-1',
        digest: 'sha256-mock-digest',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockResult),
      } as Response);

      const file = new File(['mock content'], 'test.pdf', {
        type: 'application/pdf',
      });
      const result = await issuerApi.uploadDocument(file);

      expect(result).toEqual(mockResult);
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/documents');
      expect(options?.method).toBe('POST');
      expect(options?.body).toBeInstanceOf(FormData);
    });

    it('verifyStoredDocument calls GET /api/documents/{id}/verify', async () => {
      const mockVerification = {
        verified: true,
        mode: 'MODE_A_STORED',
        message: 'Valid',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockVerification),
      } as Response);

      const result = await issuerApi.verifyStoredDocument('doc-123');
      expect(result).toEqual(mockVerification);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/documents/doc-123/verify');
      expect(options?.method).toBe('GET');
    });

    it('verifyUploadedDocument sends multipart upload to /api/documents/{id}/verify-upload', async () => {
      const mockVerification = {
        verified: true,
        mode: 'MODE_B_UPLOAD',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockVerification),
      } as Response);

      const file = new File(['mock local'], 'doc.pdf', {
        type: 'application/pdf',
      });
      const result = await issuerApi.verifyUploadedDocument('doc-123', file);

      expect(result).toEqual(mockVerification);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/documents/doc-123/verify-upload');
      expect(options?.method).toBe('POST');
      expect(options?.body).toBeInstanceOf(FormData);
    });

    it('getDocumentQrBlob fetches binary image/png blob from /api/documents/{id}/qr', async () => {
      const mockBlob = new Blob(['png-bytes'], { type: 'image/png' });
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        blob: () => Promise.resolve(mockBlob),
      } as unknown as Response);

      const blob = await issuerApi.getDocumentQrBlob('doc-qr-123');
      expect(blob).toBeInstanceOf(Blob);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/documents/doc-qr-123/qr');
      expect(options?.method).toBe('GET');
      const headers = options?.headers as Headers;
      expect(headers.get('Accept')).toBe(
        'image/png, application/octet-stream, */*',
      );
    });
  });

  describe('Credential Issuance & Verification', () => {
    it('issueCredential posts request to /api/credentials/issue', async () => {
      const mockResponse = {
        credentialId: 'cred-uuid-1',
        digest: 'sha256-cred-digest',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockResponse),
      } as Response);

      const payload = {
        issuerId: 'urn:zkdoc:issuer:alice',
        attributes: [{ name: 'degree', type: 'STRING', value: 'B.Sc.' }],
      };

      const result = await issuerApi.issueCredential(payload);
      expect(result).toEqual(mockResponse);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/credentials/issue');
      expect(options?.method).toBe('POST');
      expect(JSON.parse(options?.body as string)).toEqual(payload);
    });

    it('verifyCredential calls GET /api/credentials/{id}/verify', async () => {
      const mockVerifyResult = {
        verified: true,
        credentialId: 'cred-123',
        status: 'ACTIVE' as const,
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockVerifyResult),
      } as Response);

      const result = await issuerApi.verifyCredential('cred-123');
      expect(result).toEqual(mockVerifyResult);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/credentials/cred-123/verify');
      expect(options?.method).toBe('GET');
    });
  });

  describe('Credential Lifecycle & History', () => {
    it('getCredentialLifecycle performs GET /api/credentials/{id}/lifecycle', async () => {
      const mockLifecycle = {
        credentialId: 'cred-123',
        issuerId: 'urn:zkdoc:issuer:alice',
        status: 'ACTIVE' as const,
        version: 1,
        issuedAt: '2026-10-14T10:00:00Z',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockLifecycle),
      } as Response);

      const result = await issuerApi.getCredentialLifecycle('cred-123');
      expect(result).toEqual(mockLifecycle);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/credentials/cred-123/lifecycle');
      expect(options?.method).toBe('GET');
    });

    it('getCredentialLifecycleHistory performs GET /api/credentials/{id}/lifecycle/history', async () => {
      const mockHistory = [
        {
          id: 'evt-1',
          credentialId: 'cred-123',
          eventType: 'ISSUED',
          fromStatus: 'ACTIVE',
          toStatus: 'ACTIVE',
          actor: 'urn:zkdoc:issuer:alice',
          actorRole: 'ISSUER',
          timestamp: '2026-10-14T10:00:00Z',
        },
      ];

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockHistory),
      } as Response);

      const result = await issuerApi.getCredentialLifecycleHistory('cred-123');
      expect(result).toEqual(mockHistory);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/credentials/cred-123/lifecycle/history');
      expect(options?.method).toBe('GET');
    });

    it('suspendCredential posts to /api/credentials/{id}/suspend with reason', async () => {
      const mockSuspended = {
        credentialId: 'cred-123',
        status: 'SUSPENDED' as const,
        message: 'Credential suspended',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockSuspended),
      } as Response);

      const result = await issuerApi.suspendCredential(
        'cred-123',
        'Investigation',
      );
      expect(result).toEqual(mockSuspended);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/credentials/cred-123/suspend');
      expect(options?.method).toBe('POST');
      expect(JSON.parse(options?.body as string)).toEqual({
        reason: 'Investigation',
      });
    });

    it('resumeCredential posts to /api/credentials/{id}/resume with reason', async () => {
      const mockResumed = {
        credentialId: 'cred-123',
        status: 'ACTIVE' as const,
        message: 'Credential resumed',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockResumed),
      } as Response);

      const result = await issuerApi.resumeCredential(
        'cred-123',
        'Investigation cleared',
      );
      expect(result).toEqual(mockResumed);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/credentials/cred-123/resume');
      expect(options?.method).toBe('POST');
      expect(JSON.parse(options?.body as string)).toEqual({
        reason: 'Investigation cleared',
      });
    });

    it('revokeCredential posts to /api/credentials/{id}/revoke with reason', async () => {
      const mockRevoked = {
        credentialId: 'cred-123',
        status: 'REVOKED' as const,
        message: 'Credential permanently revoked',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockRevoked),
      } as Response);

      const result = await issuerApi.revokeCredential('cred-123', 'Offboarded');
      expect(result).toEqual(mockRevoked);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/credentials/cred-123/revoke');
      expect(options?.method).toBe('POST');
      expect(JSON.parse(options?.body as string)).toEqual({
        reason: 'Offboarded',
      });
    });
  });

  describe('Capabilities Operations', () => {
    it('createCapability posts to /api/capabilities', async () => {
      const mockCap = {
        capabilityToken: 'cap-tok-123',
        documentId: 'doc-123',
        mode: 'MODE_A_PRESENTED_ARTIFACT' as const,
        expiresAt: '2026-10-14T20:00:00Z',
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockCap),
      } as Response);

      const req = {
        documentId: 'doc-123',
        mode: 'MODE_A_PRESENTED_ARTIFACT' as const,
        ttlSeconds: 3600,
      };

      const res = await issuerApi.createCapability(req);
      expect(res).toEqual(mockCap);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/capabilities');
      expect(options?.method).toBe('POST');
      expect(JSON.parse(options?.body as string)).toEqual(req);
    });

    it('getCapabilityQrBlob fetches binary image/png blob from /api/capabilities/{token}/qr', async () => {
      const mockBlob = new Blob(['cap-png-bytes'], { type: 'image/png' });
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        status: 200,
        blob: () => Promise.resolve(mockBlob),
      } as unknown as Response);

      const blob = await issuerApi.getCapabilityQrBlob('cap-tok-123');
      expect(blob).toBeInstanceOf(Blob);
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe('/api/capabilities/cap-tok-123/qr');
      expect(options?.method).toBe('GET');
    });
  });
});
