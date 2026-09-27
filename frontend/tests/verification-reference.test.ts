import {
  parseVerificationReference,
  VerificationReferenceError,
} from '../src/features/verification/validation/verificationReference';

describe('verification reference validation', () => {
  const currentToken = 'A'.repeat(43);
  const legacyToken = 'a'.repeat(64);

  it('accepts current and legacy opaque token formats', () => {
    expect(parseVerificationReference(currentToken)).toBe(currentToken);
    expect(parseVerificationReference(legacyToken)).toBe(legacyToken);
  });

  it('extracts the token from the backend QR URL without navigating to it', () => {
    expect(
      parseVerificationReference(
        `https://verify.example/api/public/verify/${currentToken}`,
      ),
    ).toBe(currentToken);
  });

  it.each([
    '',
    'https://example.com/not-a-verification-route',
    `javascript:alert(1)`,
    `https://user:password@example.com/api/public/verify/${currentToken}`,
    `https://example.com/api/public/verify/${currentToken}?leak=true`,
    '<script>alert(1)</script>',
  ])('rejects unsupported or unsafe input: %s', (value) => {
    expect(() => parseVerificationReference(value)).toThrow(
      VerificationReferenceError,
    );
  });
});
