import { describe, it, expect } from 'vitest';
import {
  parseQrPayload,
  hexToBase64,
  base64ToHex,
  isUuid,
  MAX_QR_PAYLOAD_LENGTH,
} from '../src/lib/utils/qrPayload';

describe('QR Payload Parser & Validation Utility', () => {
  it('parses valid canonical JSON payload in MODE_B', () => {
    const raw = JSON.stringify({
      v: 'QR_V1',
      m: 'MODE_B_REGISTRY_CAPABILITY',
      tok: 'test-capability-token-1234567890123456',
      uri: 'https://zkdoc.internal/api/public/verify/test-capability-token-1234567890123456',
    });

    const result = parseQrPayload(raw);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.payload.token).toBe(
        'test-capability-token-1234567890123456',
      );
      expect(result.payload.mode).toBe('MODE_B_REGISTRY_CAPABILITY');
      expect(result.payload.version).toBe('QR_V1');
      expect(result.payload.format).toBe('CANONICAL_JSON');
    }
  });

  it('parses valid canonical JSON payload in MODE_A', () => {
    const raw = JSON.stringify({
      v: 'QR_V1',
      m: 'MODE_A_PRESENTED_ARTIFACT',
      tok: 'artifact-token-123456789012345678',
      uri: '/api/public/verify/artifact-token-123456789012345678',
    });

    const result = parseQrPayload(raw);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.payload.token).toBe('artifact-token-123456789012345678');
      expect(result.payload.mode).toBe('MODE_A_PRESENTED_ARTIFACT');
      expect(result.payload.format).toBe('CANONICAL_JSON');
    }
  });

  it('rejects canonical JSON with unsupported protocol version', () => {
    const raw = JSON.stringify({
      v: 'QR_V2_UNSUPPORTED',
      m: 'MODE_B_REGISTRY_CAPABILITY',
      tok: 'test-capability-token-1234567890123456',
      uri: 'https://example.com',
    });

    const result = parseQrPayload(raw);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error).toContain('Unsupported QR protocol version');
    }
  });

  it('rejects canonical JSON with invalid verification mode', () => {
    const raw = JSON.stringify({
      v: 'QR_V1',
      m: 'MODE_INVALID',
      tok: 'test-capability-token-1234567890123456',
      uri: 'https://example.com',
    });

    const result = parseQrPayload(raw);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error).toContain('Unsupported QR verification mode');
    }
  });

  it('rejects canonical JSON with too short token', () => {
    const raw = JSON.stringify({
      v: 'QR_V1',
      m: 'MODE_B_REGISTRY_CAPABILITY',
      tok: 'short',
      uri: 'https://example.com',
    });

    const result = parseQrPayload(raw);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error).toContain('Invalid capabilityToken');
    }
  });

  it('parses valid direct verification URLs and strips query/hash', () => {
    const url =
      'https://zkdoc.example.com/api/public/verify/valid-token-1234567890123456?source=camera#details';
    const result = parseQrPayload(url);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.payload.token).toBe('valid-token-1234567890123456');
      expect(result.payload.format).toBe('DIRECT_URL');
    }
  });

  it('parses direct URLs with capability path', () => {
    const url =
      'https://zkdoc.example.com/api/public/verify/capability/valid-token-1234567890123456';
    const result = parseQrPayload(url);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.payload.token).toBe('valid-token-1234567890123456');
      expect(result.payload.format).toBe('DIRECT_URL');
    }
  });

  it('parses frontend verify URLs with token query param', () => {
    const url =
      'https://zkdoc.example.com/verify?token=valid-token-1234567890123456';
    const result = parseQrPayload(url);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.payload.token).toBe('valid-token-1234567890123456');
      expect(result.payload.format).toBe('DIRECT_URL');
    }
  });

  it('parses raw opaque tokens', () => {
    const raw = 'opaque-token-1234567890123456';
    const result = parseQrPayload(raw);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.payload.token).toBe('opaque-token-1234567890123456');
      expect(result.payload.format).toBe('RAW_TOKEN');
    }
  });

  it('rejects dangerous javascript: and data: schemes', () => {
    const jsResult = parseQrPayload('javascript:alert(document.cookie)');
    expect(jsResult.success).toBe(false);
    if (!jsResult.success) {
      expect(jsResult.error).toContain('Dangerous or unsupported URL scheme');
    }

    const dataResult = parseQrPayload(
      'data:text/html,<script>alert(1)</script>',
    );
    expect(dataResult.success).toBe(false);
  });

  it('rejects empty or null inputs', () => {
    expect(parseQrPayload('').success).toBe(false);
    expect(parseQrPayload('   ').success).toBe(false);
    expect(parseQrPayload(null).success).toBe(false);
    expect(parseQrPayload(undefined).success).toBe(false);
  });

  it('rejects payloads exceeding max length', () => {
    const longString = 'a'.repeat(MAX_QR_PAYLOAD_LENGTH + 1);
    const result = parseQrPayload(longString);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error).toContain('exceeds maximum length');
    }
  });

  it('converts hex to Base64 and back with full fidelity', () => {
    const originalHex = '48656c6c6f205a65726f204b6e6f776c65646765';
    const b64 = hexToBase64(originalHex);
    expect(b64).toBeTruthy();
    const roundTripHex = base64ToHex(b64);
    expect(roundTripHex).toBe(originalHex.toLowerCase());
  });

  it('validates UUIDs correctly', () => {
    expect(isUuid('f47ac10b-58cc-4372-a567-0e02b2c3d479')).toBe(true);
    expect(isUuid('F47AC10B-58CC-4372-A567-0E02B2C3D479')).toBe(true);
    expect(isUuid('not-a-uuid')).toBe(false);
    expect(isUuid('')).toBe(false);
  });
});
