import { describe, it, expect } from 'vitest';
import { validateReturnUrl } from '../src/lib/utils/security';

describe('Security: validateReturnUrl (Open Redirect Defense CWE-601)', () => {
  it('accepts safe relative pathnames', () => {
    expect(validateReturnUrl('/credentials')).toBe('/credentials');
    expect(validateReturnUrl('/issuer')).toBe('/issuer');
    expect(validateReturnUrl('/verify')).toBe('/verify');
    expect(validateReturnUrl('/administration')).toBe('/administration');
  });

  it('preserves valid query parameters and URL fragments on safe paths', () => {
    expect(validateReturnUrl('/credentials?status=ACTIVE&page=1')).toBe(
      '/credentials?status=ACTIVE&page=1',
    );
    expect(validateReturnUrl('/audit-logs?size=50#bottom')).toBe(
      '/audit-logs?size=50#bottom',
    );
  });

  it('rejects external absolute URLs', () => {
    expect(validateReturnUrl('https://evil.attacker.com')).toBe('/');
    expect(validateReturnUrl('http://evil.attacker.com/credentials')).toBe('/');
    expect(validateReturnUrl('ftp://evil.attacker.com')).toBe('/');
  });

  it('rejects protocol-relative URLs (//)', () => {
    expect(validateReturnUrl('//evil.attacker.com')).toBe('/');
    expect(validateReturnUrl('//evil.attacker.com/credentials')).toBe('/');
    expect(validateReturnUrl('///evil.attacker.com')).toBe('/');
  });

  it('rejects backslash bypass vectors (/\\ and \\)', () => {
    expect(validateReturnUrl('/\\evil.attacker.com')).toBe('/');
    expect(validateReturnUrl('\\evil.attacker.com')).toBe('/');
    expect(validateReturnUrl('/credentials\\../evil')).toBe('/');
  });

  it('rejects inline scheme execution payloads (javascript:, data:)', () => {
    expect(validateReturnUrl('javascript:alert(1)')).toBe('/');
    expect(validateReturnUrl('/javascript:alert(1)')).toBe('/');
    expect(validateReturnUrl('data:text/html,<script>alert(1)</script>')).toBe(
      '/',
    );
  });

  it('falls back to provided fallback URL when candidate is invalid or null', () => {
    expect(validateReturnUrl(null, '/overview')).toBe('/overview');
    expect(validateReturnUrl(undefined, '/dashboard')).toBe('/dashboard');
    expect(validateReturnUrl('', '/home')).toBe('/home');
    expect(validateReturnUrl('   ', '/default')).toBe('/default');
  });
});
