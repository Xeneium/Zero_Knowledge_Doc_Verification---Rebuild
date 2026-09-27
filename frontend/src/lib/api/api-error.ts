export type ApiErrorKind =
  'http' | 'network' | 'timeout' | 'cancelled' | 'invalid-response';

interface ApiErrorOptions {
  kind: ApiErrorKind;
  status?: number;
  code?: string;
  cause?: unknown;
}

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status?: number;
  readonly code?: string;

  constructor(message: string, options: ApiErrorOptions) {
    super(message, { cause: options.cause });
    this.name = 'ApiError';
    this.kind = options.kind;
    this.status = options.status;
    this.code = options.code;
  }
}
