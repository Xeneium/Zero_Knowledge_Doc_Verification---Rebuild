import { ApiError } from './api-error';
import { apiClient } from './client';

export interface HealthResponse {
  status: 'ok';
}

export async function getBackendHealth(
  signal?: AbortSignal,
): Promise<HealthResponse> {
  const response = await apiClient.get<unknown>('/api/health', {
    signal,
    timeoutMs: 5_000,
  });

  if (
    typeof response !== 'object' ||
    response === null ||
    !('status' in response) ||
    response.status !== 'ok'
  ) {
    throw new ApiError('The backend returned an unexpected health response.', {
      kind: 'invalid-response',
    });
  }

  return { status: 'ok' };
}
