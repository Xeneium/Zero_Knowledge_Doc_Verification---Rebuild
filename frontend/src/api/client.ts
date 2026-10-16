import { authStore } from '../state/authStore';
import { ApiError } from './errors';
import type { ApiErrorResponse } from './types';

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
}

export async function apiRequest<T>(
  endpoint: string,
  options: RequestOptions = {},
): Promise<T> {
  const correlationId = crypto.randomUUID();
  const requestEpoch = authStore.getSessionEpoch();
  const token = authStore.getState().token;

  const headers = new Headers(options.headers);
  headers.set('X-Correlation-Id', correlationId);
  headers.set('Accept', 'application/json');

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  let bodyContent: BodyInit | null = null;
  if (options.body !== undefined && options.body !== null) {
    if (options.body instanceof FormData || typeof options.body === 'string') {
      bodyContent = options.body as BodyInit;
    } else {
      headers.set('Content-Type', 'application/json');
      bodyContent = JSON.stringify(options.body);
    }
  }

  const response = await fetch(endpoint, {
    method: options.method || 'GET',
    ...options,
    headers,
    body: bodyContent,
  });

  // Stale async response rejection: if session changed/terminated while request was in-flight, discard
  if (token && authStore.getSessionEpoch() !== requestEpoch) {
    throw new ApiError({
      timestamp: new Date().toISOString(),
      status: 499,
      error: 'Client Session Terminated',
      message:
        'Request discarded because authenticated session was terminated or switched while in flight',
      path: endpoint,
      correlationId,
    });
  }

  if (!response.ok) {
    if (response.status === 401) {
      authStore.clearSession();
    }

    let errorData: ApiErrorResponse;
    try {
      errorData = (await response.json()) as ApiErrorResponse;
    } catch {
      errorData = {
        timestamp: new Date().toISOString(),
        status: response.status,
        error: response.statusText,
        message: `HTTP error ${response.status}`,
        path: endpoint,
        correlationId,
      };
    }

    throw new ApiError(errorData);
  }

  if (response.status === 204) {
    return {} as T;
  }

  return (await response.json()) as T;
}

export async function apiBlobRequest(
  endpoint: string,
  options: RequestOptions = {},
): Promise<Blob> {
  const correlationId = crypto.randomUUID();
  const requestEpoch = authStore.getSessionEpoch();
  const token = authStore.getState().token;

  const headers = new Headers(options.headers);
  headers.set('X-Correlation-Id', correlationId);
  if (!headers.has('Accept')) {
    headers.set('Accept', 'image/png, application/octet-stream, */*');
  }

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  let bodyContent: BodyInit | null = null;
  if (options.body !== undefined && options.body !== null) {
    if (options.body instanceof FormData || typeof options.body === 'string') {
      bodyContent = options.body as BodyInit;
    } else {
      headers.set('Content-Type', 'application/json');
      bodyContent = JSON.stringify(options.body);
    }
  }

  const response = await fetch(endpoint, {
    method: options.method || 'GET',
    ...options,
    headers,
    body: bodyContent,
  });

  // Stale async response rejection: if session changed/terminated while request was in-flight, discard
  if (token && authStore.getSessionEpoch() !== requestEpoch) {
    throw new ApiError({
      timestamp: new Date().toISOString(),
      status: 499,
      error: 'Client Session Terminated',
      message:
        'Request discarded because authenticated session was terminated or switched while in flight',
      path: endpoint,
      correlationId,
    });
  }

  if (!response.ok) {
    if (response.status === 401) {
      authStore.clearSession();
    }

    let errorData: ApiErrorResponse;
    try {
      errorData = (await response.json()) as ApiErrorResponse;
    } catch {
      errorData = {
        timestamp: new Date().toISOString(),
        status: response.status,
        error: response.statusText,
        message: `HTTP error ${response.status}`,
        path: endpoint,
        correlationId,
      };
    }

    throw new ApiError(errorData);
  }

  return await response.blob();
}
