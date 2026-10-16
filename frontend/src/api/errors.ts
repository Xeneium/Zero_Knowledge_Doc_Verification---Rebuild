import type { ApiErrorResponse } from './types';

export class ApiError extends Error {
  public readonly status: number;
  public readonly error: string;
  public readonly path: string;
  public readonly correlationId?: string;
  public readonly validationErrors?: Record<string, string>;
  public readonly timestamp: string;

  constructor(payload: ApiErrorResponse) {
    super(payload.message || 'An unexpected API error occurred');
    this.name = 'ApiError';
    this.status = payload.status;
    this.error = payload.error;
    this.path = payload.path;
    this.correlationId = payload.correlationId;
    this.validationErrors = payload.validationErrors;
    this.timestamp = payload.timestamp;
  }

  public get isValidationError(): boolean {
    return this.status === 400 && !!this.validationErrors;
  }

  public get isUnauthorized(): boolean {
    return this.status === 401;
  }

  public get isForbidden(): boolean {
    return this.status === 403;
  }

  public get isNotFound(): boolean {
    return this.status === 404;
  }
}
