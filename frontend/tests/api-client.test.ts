import { ApiError } from '../src/lib/api/api-error';
import { apiRequest } from '../src/lib/api/client';

describe('api client', () => {
  it('returns a typed JSON response for a successful request', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ status: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await expect(
      apiRequest<{ status: string }>('/api/health'),
    ).resolves.toEqual({ status: 'ok' });
  });

  it('normalizes HTTP errors without exposing server messages', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          message: 'Bearer private-token-value',
          code: 'AUTH_REQUIRED',
        }),
        {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );

    const error = await apiRequest('/api/private').catch(
      (reason: unknown) => reason,
    );
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      message: 'Authentication is required.',
      kind: 'http',
      status: 401,
      code: 'AUTH_REQUIRED',
    });
    expect(String(error)).not.toContain('private-token-value');
  });

  it('drops unsafe server-provided error codes', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ code: 'secret token value' }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await expect(apiRequest('/api/private')).rejects.toMatchObject({
      message: 'The service could not complete the request.',
      kind: 'http',
      status: 500,
      code: undefined,
    });
  });

  it('normalizes network failures', async () => {
    vi.mocked(fetch).mockRejectedValueOnce(new TypeError('Failed to fetch'));

    await expect(apiRequest('/api/health')).rejects.toMatchObject({
      message: 'The server could not be reached.',
      kind: 'network',
    });
  });

  it('supports empty successful responses', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 204 }));

    await expect(
      apiRequest<void>('/api/resource', { method: 'DELETE' }),
    ).resolves.toBeUndefined();
  });
});
