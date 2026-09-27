const MAX_REFERENCE_LENGTH = 2_048;
const CURRENT_TOKEN = /^[A-Za-z0-9_-]{43}$/;
const LEGACY_TOKEN = /^[a-f0-9]{64}$/;
const PUBLIC_PATH_PREFIX = '/api/public/verify/';

export class VerificationReferenceError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'VerificationReferenceError';
  }
}

function validateToken(token: string): string {
  if (CURRENT_TOKEN.test(token) || LEGACY_TOKEN.test(token)) return token;
  throw new VerificationReferenceError(
    'Enter a valid document verification reference.',
  );
}

export function parseVerificationReference(rawValue: string): string {
  const value = rawValue.trim();
  if (!value)
    throw new VerificationReferenceError('Enter a verification reference.');
  if (value.length > MAX_REFERENCE_LENGTH) {
    throw new VerificationReferenceError(
      'The verification reference is too long.',
    );
  }

  if (CURRENT_TOKEN.test(value) || LEGACY_TOKEN.test(value)) return value;

  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new VerificationReferenceError(
      'Enter a valid verification URL or reference.',
    );
  }

  if (
    !['http:', 'https:'].includes(url.protocol) ||
    url.username ||
    url.password ||
    url.search ||
    url.hash ||
    !url.pathname.startsWith(PUBLIC_PATH_PREFIX)
  ) {
    throw new VerificationReferenceError(
      'This QR code is not a supported verification link.',
    );
  }

  const token = url.pathname.slice(PUBLIC_PATH_PREFIX.length);
  if (!token || token.includes('/')) {
    throw new VerificationReferenceError(
      'This QR code is not a supported verification link.',
    );
  }

  return validateToken(token);
}
