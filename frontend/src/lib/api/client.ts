import { env } from '../../config/env';
import { ApiError } from './api-error';

const DEFAULT_TIMEOUT_MS = 10_000;

export interface ApiRequestOptions extends Omit<RequestInit, 'body'> {
  body?: BodyInit | Record<string, unknown> | null;
  timeoutMs?: number;
}

interface ErrorPayload {
  code?: unknown;
}

function httpErrorMessage(status: number): string {
  if (status === 400 || status === 422) return 'The request was not valid.';
  if (status === 401) return 'Authentication is required.';
  if (status === 403) return 'You do not have permission for this request.';
  if (status === 404) return 'The requested resource was not found.';
  if (status === 409) return 'The request conflicts with the current state.';
  if (status === 429) return 'Too many requests were made. Try again later.';
  if (status >= 500) return 'The service could not complete the request.';
  return `The request failed with status ${status}.`;
}

function safeErrorCode(value: unknown): string | undefined {
  return typeof value === 'string' && /^[A-Z0-9_]{1,64}$/.test(value)
    ? value
    : undefined;
}

function buildUrl(path: string): string {
  if (!path.startsWith('/')) {
    throw new ApiError('API paths must begin with a slash.', {
      kind: 'invalid-response',
    });
  }
  return `${env.apiBaseUrl}${path}`;
}

function isJsonContent(response: Response): boolean {
  return (
    response.headers
      .get('content-type')
      ?.toLowerCase()
      .includes('application/json') ?? false
  );
}

async function toApiError(response: Response): Promise<ApiError> {
  let payload: ErrorPayload | undefined;

  if (isJsonContent(response)) {
    try {
      payload = (await response.json()) as ErrorPayload;
    } catch {
      // Invalid error bodies are deliberately reduced to the HTTP status below.
    }
  }

  return new ApiError(httpErrorMessage(response.status), {
    kind: 'http',
    status: response.status,
    code: safeErrorCode(payload?.code),
  });
}

function serializeBody(
  body: ApiRequestOptions['body'],
  headers: Headers,
): BodyInit | null | undefined {
  if (
    body === null ||
    body === undefined ||
    body instanceof FormData ||
    typeof body === 'string'
  ) {
    return body;
  }

  if (
    body instanceof Blob ||
    body instanceof URLSearchParams ||
    body instanceof ArrayBuffer
  ) {
    return body;
  }

  headers.set('Content-Type', 'application/json');
  return JSON.stringify(body);
}

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const {
    timeoutMs = DEFAULT_TIMEOUT_MS,
    signal: externalSignal,
    body,
    ...requestInit
  } = options;
  const timeoutController = new AbortController();
  const timeout = window.setTimeout(
    () => timeoutController.abort('timeout'),
    timeoutMs,
  );
  const signal = externalSignal
    ? AbortSignal.any([externalSignal, timeoutController.signal])
    : timeoutController.signal;
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');

  try {
    const response = await fetch(buildUrl(path), {
      ...requestInit,
      headers,
      body: serializeBody(body, headers),
      signal,
    });

    if (!response.ok) {
      throw await toApiError(response);
    }

    if (
      response.status === 204 ||
      response.headers.get('content-length') === '0'
    ) {
      return undefined as T;
    }

    if (!isJsonContent(response)) {
      throw new ApiError(
        'The server returned an unsupported response format.',
        {
          kind: 'invalid-response',
          status: response.status,
        },
      );
    }

    try {
      return (await response.json()) as T;
    } catch (cause) {
      throw new ApiError('The server returned malformed JSON.', {
        kind: 'invalid-response',
        status: response.status,
        cause,
      });
    }
  } catch (error) {
    if (error instanceof ApiError) throw error;

    if (signal.aborted) {
      const timedOut =
        timeoutController.signal.aborted && !externalSignal?.aborted;
      throw new ApiError(
        timedOut ? 'The request timed out.' : 'The request was cancelled.',
        {
          kind: timedOut ? 'timeout' : 'cancelled',
          cause: error,
        },
      );
    }

    throw new ApiError('The server could not be reached.', {
      kind: 'network',
      cause: error,
    });
  } finally {
    window.clearTimeout(timeout);
  }
}

export const apiClient = {
  get: <T>(path: string, options?: ApiRequestOptions) =>
    apiRequest<T>(path, { ...options, method: 'GET' }),
  post: <T>(
    path: string,
    body?: ApiRequestOptions['body'],
    options?: ApiRequestOptions,
  ) => apiRequest<T>(path, { ...options, method: 'POST', body }),
};
