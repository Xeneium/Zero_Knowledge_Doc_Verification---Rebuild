/**
 * Security utilities for URL sanitization and return-path validation.
 * Defends against Open Redirect vulnerabilities (CWE-601).
 */

/**
 * Validates a candidate return URL to ensure it is a safe relative path on the same origin.
 *
 * Rules enforced:
 * - Must start with a single forward slash ('/').
 * - Must NOT start with '//' (protocol-relative external URL).
 * - Must NOT start with '/\\' (backslash path traversal or scheme bypass in certain browsers).
 * - Must NOT contain a colon (':') in the path segment before queries/fragments (prevents 'javascript:', 'data:', etc.).
 * - Must NOT contain backslashes ('\\').
 * - Must parse safely against a synthetic base without changing the origin.
 *
 * @param candidate The unvalidated candidate URL from query params or route state.
 * @param fallback The safe default URL to return if the candidate is rejected (defaults to '/').
 * @returns The sanitized safe relative path or the fallback URL.
 */
export function validateReturnUrl(
  candidate: string | null | undefined,
  fallback = '/',
): string {
  if (!candidate || typeof candidate !== 'string') {
    return fallback;
  }

  const trimmed = candidate.trim();

  // Must start with '/' but not '//' or '/\'
  if (
    !trimmed.startsWith('/') ||
    trimmed.startsWith('//') ||
    trimmed.startsWith('/\\')
  ) {
    return fallback;
  }

  // Reject any protocol scheme prefix (e.g., 'http:', 'https:', 'javascript:', 'data:')
  if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(trimmed)) {
    return fallback;
  }

  // Disallow colon or backslash anywhere in the path segment before '?' or '#'
  const pathOnly = trimmed.split('?')[0].split('#')[0];
  if (pathOnly.includes(':') || pathOnly.includes('\\')) {
    return fallback;
  }

  // Parse against synthetic origin to guarantee origin invariance
  try {
    const syntheticOrigin = 'https://zkdoc.internal';
    const parsed = new URL(trimmed, syntheticOrigin);

    if (parsed.origin !== syntheticOrigin) {
      return fallback;
    }

    return parsed.pathname + parsed.search + parsed.hash;
  } catch {
    return fallback;
  }
}
