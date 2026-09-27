export interface AppEnvironment {
  apiBaseUrl: string;
}

export class ConfigurationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ConfigurationError';
  }
}

export function parseApiBaseUrl(value: string | undefined): string {
  const candidate = value?.trim() || '/';

  if (candidate.startsWith('/')) {
    return candidate === '/' ? '' : candidate.replace(/\/$/, '');
  }

  let url: URL;
  try {
    url = new URL(candidate);
  } catch {
    throw new ConfigurationError(
      'VITE_API_BASE_URL must be an absolute http(s) URL or a root-relative path.',
    );
  }

  if (
    !['http:', 'https:'].includes(url.protocol) ||
    url.username ||
    url.password
  ) {
    throw new ConfigurationError(
      'VITE_API_BASE_URL must use http(s) and must not contain credentials.',
    );
  }

  return url.toString().replace(/\/$/, '');
}

export const env: AppEnvironment = Object.freeze({
  apiBaseUrl: parseApiBaseUrl(import.meta.env.VITE_API_BASE_URL),
});
