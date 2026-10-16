import type { VerificationMode } from '../../api/types';

export const MAX_QR_PAYLOAD_LENGTH = 2048;

export interface ParsedQrPayload {
  token: string;
  mode?: VerificationMode;
  version?: string;
  format: 'CANONICAL_JSON' | 'DIRECT_URL' | 'RAW_TOKEN';
  originalUri?: string;
}

export type QrParseResult =
  | { success: true; payload: ParsedQrPayload }
  | { success: false; error: string };

/**
 * Validates and parses untrusted QR code payload strings into structured verification metadata.
 * Defends against excessive payload size, malicious URL schemes, and format injection.
 */
export function parseQrPayload(
  rawInput: string | null | undefined,
): QrParseResult {
  if (!rawInput || typeof rawInput !== 'string') {
    return { success: false, error: 'QR payload cannot be empty' };
  }

  const trimmed = rawInput.trim();
  if (trimmed.length === 0) {
    return { success: false, error: 'QR payload cannot be empty' };
  }

  if (trimmed.length > MAX_QR_PAYLOAD_LENGTH) {
    return {
      success: false,
      error: `QR payload exceeds maximum length of ${MAX_QR_PAYLOAD_LENGTH} characters`,
    };
  }

  // Defend against unsafe execution schemes
  const lower = trimmed.toLowerCase();
  if (
    lower.startsWith('javascript:') ||
    lower.startsWith('vbscript:') ||
    lower.startsWith('data:') ||
    lower.startsWith('file:')
  ) {
    return {
      success: false,
      error: 'Dangerous or unsupported URL scheme in QR code',
    };
  }

  // 1. Try parsing as Canonical JSON payload
  if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
    try {
      const parsed = JSON.parse(trimmed) as Record<string, unknown>;
      if (typeof parsed !== 'object' || parsed === null) {
        return { success: false, error: 'Malformed JSON in QR payload' };
      }

      const version = parsed.v;
      const mode = parsed.m;
      const tok = parsed.tok;
      const uri = parsed.uri;

      if (version !== 'QR_V1') {
        return {
          success: false,
          error: `Unsupported QR protocol version: ${String(version)}`,
        };
      }

      if (
        mode !== 'MODE_A_PRESENTED_ARTIFACT' &&
        mode !== 'MODE_B_REGISTRY_CAPABILITY'
      ) {
        return {
          success: false,
          error: `Unsupported QR verification mode: ${String(mode)}`,
        };
      }

      if (
        typeof tok !== 'string' ||
        tok.length < 16 ||
        tok.length > 128 ||
        !/^[A-Za-z0-9_-]+$/.test(tok)
      ) {
        return {
          success: false,
          error: 'Invalid capabilityToken in canonical QR payload',
        };
      }

      return {
        success: true,
        payload: {
          token: tok,
          mode,
          version: String(version),
          format: 'CANONICAL_JSON',
          originalUri: typeof uri === 'string' ? uri : undefined,
        },
      };
    } catch {
      return { success: false, error: 'Invalid JSON syntax in QR payload' };
    }
  }

  // 2. Try parsing as Direct URL
  if (
    trimmed.startsWith('http://') ||
    trimmed.startsWith('https://') ||
    trimmed.startsWith('/')
  ) {
    // Check for /api/public/verify/{token} or /verify/{token}
    const verifyPrefixes = ['/api/public/verify/', '/verify/'];
    for (const prefix of verifyPrefixes) {
      const idx = trimmed.indexOf(prefix);
      if (idx !== -1) {
        let tokenPart = trimmed.substring(idx + prefix.length);
        // Strip capability/ if present in prefix path
        if (tokenPart.startsWith('capability/')) {
          tokenPart = tokenPart.substring('capability/'.length);
        }
        const queryIdx = tokenPart.indexOf('?');
        if (queryIdx !== -1) tokenPart = tokenPart.substring(0, queryIdx);
        const hashIdx = tokenPart.indexOf('#');
        if (hashIdx !== -1) tokenPart = tokenPart.substring(0, hashIdx);

        if (
          tokenPart.length >= 16 &&
          tokenPart.length <= 128 &&
          /^[A-Za-z0-9_-]+$/.test(tokenPart)
        ) {
          return {
            success: true,
            payload: {
              token: tokenPart,
              format: 'DIRECT_URL',
              originalUri: trimmed,
            },
          };
        }
      }
    }

    // Check for query param ?token=... or &token=...
    const urlPattern = /[?&]token=([A-Za-z0-9_-]+)/;
    const match = trimmed.match(urlPattern);
    if (match && match[1]) {
      const token = match[1];
      if (token.length >= 16 && token.length <= 128) {
        return {
          success: true,
          payload: {
            token,
            format: 'DIRECT_URL',
            originalUri: trimmed,
          },
        };
      }
    }
  }

  // 3. Raw Opaque Token
  if (
    trimmed.length >= 16 &&
    trimmed.length <= 128 &&
    /^[A-Za-z0-9_-]+$/.test(trimmed)
  ) {
    return {
      success: true,
      payload: {
        token: trimmed,
        format: 'RAW_TOKEN',
      },
    };
  }

  return {
    success: false,
    error:
      'Unrecognized QR payload format. Expected canonical JSON, verification URL, or token.',
  };
}

/**
 * Converts a hex string into a standard Base64 string.
 * Used for submitting hex nonce and proof bytes to the Jackson byte[] endpoints.
 */
export function hexToBase64(hex: string): string {
  const cleanHex = hex.trim().replace(/^0x/i, '');
  if (cleanHex.length % 2 !== 0) {
    throw new Error('Invalid hex string length');
  }
  const bytes = new Uint8Array(cleanHex.length / 2);
  for (let i = 0; i < cleanHex.length; i += 2) {
    bytes[i / 2] = parseInt(cleanHex.substring(i, i + 2), 16);
  }
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

/**
 * Converts a Base64 string into a lowercase hex string.
 */
export function base64ToHex(b64: string): string {
  const binary = atob(b64.trim());
  let hex = '';
  for (let i = 0; i < binary.length; i++) {
    hex += binary.charCodeAt(i).toString(16).padStart(2, '0');
  }
  return hex;
}

/**
 * Checks whether an input string is a valid UUID (v4/canonical).
 */
export function isUuid(input: string): boolean {
  return /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(
    input.trim(),
  );
}
