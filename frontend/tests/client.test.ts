import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { apiRequest } from '../src/api/client';
import { authStore } from '../src/state/authStore';
import { ApiError } from '../src/api/errors';

describe('apiRequest Client Foundation', () => {
  beforeEach(() => {
    authStore.clearSession();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('attaches X-Correlation-Id and Accept headers on GET request', async () => {
    const mockResponse = { status: 'UP' };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockResponse),
    } as Response);

    const result = await apiRequest<{ status: string }>('/actuator/health');

    expect(result).toEqual(mockResponse);
    expect(fetchSpy).toHaveBeenCalledTimes(1);

    const callArgs = fetchSpy.mock.calls[0];
    const headers = callArgs[1]?.headers as Headers;
    expect(headers.get('Accept')).toBe('application/json');
    expect(headers.get('X-Correlation-Id')).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );
  });

  it('attaches Bearer token when authStore contains a valid token', async () => {
    // Generate valid JWT structure: header.payload.signature
    const payload = btoa(
      JSON.stringify({
        sub: 'issuer-admin',
        roles: ['ROLE_ISSUER'],
        exp: 9999999999,
      }),
    );
    const dummyToken = `eyJhbGciOiJSUzI1NiJ9.${payload}.sig`;
    authStore.setToken(dummyToken);

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ success: true }),
    } as Response);

    await apiRequest('/api/issuer/credentials');

    const headers = fetchSpy.mock.calls[0][1]?.headers as Headers;
    expect(headers.get('Authorization')).toBe(`Bearer ${dummyToken}`);
  });

  it('normalizes error response into ApiError with validation errors and correlationId', async () => {
    const errorPayload = {
      timestamp: '2026-10-14T21:00:00Z',
      status: 400,
      error: 'Bad Request',
      message: 'Field nonceHex cannot be blank',
      path: '/api/holder/credentials/proofs/generate',
      correlationId: '12345678-1234-1234-1234-123456789abc',
      validationErrors: { nonceHex: 'must match ^[0-9a-fA-F]{2,128}$' },
    };

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      json: () => Promise.resolve(errorPayload),
    } as Response);

    await expect(
      apiRequest('/api/holder/credentials/proofs/generate'),
    ).rejects.toThrow(ApiError);

    try {
      await apiRequest('/api/holder/credentials/proofs/generate');
    } catch (err) {
      if (err instanceof ApiError) {
        expect(err.status).toBe(400);
        expect(err.correlationId).toBe(errorPayload.correlationId);
        expect(err.validationErrors?.nonceHex).toBeDefined();
        expect(err.isValidationError).toBe(true);
      }
    }
  });

  it('clears session in authStore on 401 Unauthorized response', async () => {
    const payload = btoa(
      JSON.stringify({
        sub: 'user-1',
        roles: ['ROLE_HOLDER'],
        exp: 9999999999,
      }),
    );
    authStore.setToken(`header.${payload}.sig`);
    expect(authStore.getState().isAuthenticated).toBe(true);

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 401,
      statusText: 'Unauthorized',
      json: () =>
        Promise.resolve({
          timestamp: '2026-10-14T21:00:00Z',
          status: 401,
          error: 'Unauthorized',
          message: 'JWT expired',
          path: '/api/holder/credentials',
        }),
    } as Response);

    await expect(apiRequest('/api/holder/credentials')).rejects.toThrow(
      ApiError,
    );
    expect(authStore.getState().isAuthenticated).toBe(false);
    expect(authStore.getState().token).toBeNull();
  });
});
