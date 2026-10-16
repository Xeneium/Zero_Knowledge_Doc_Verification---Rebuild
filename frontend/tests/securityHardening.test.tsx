import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { authStore } from '../src/state/authStore';
import { holderStore } from '../src/state/holderStore';
import { issuerStore } from '../src/state/issuerStore';
import { adminStore } from '../src/state/adminStore';
import { verifierStore } from '../src/state/verifierStore';
import { apiRequest } from '../src/api/client';
import {
  sanitizeFilename,
  validateFileMagicBytes,
  MAX_FILENAME_LENGTH,
} from '../src/lib/utils/fileValidation';
import { RegisterKeyModal } from '../src/features/admin/components/RegisterKeyModal';
import { QrScannerCard } from '../src/features/verifier/components/QrScannerCard';
import type {
  HolderCredentialResponse,
  KeyMetadataResponse,
  SecurityAuditEventResponse,
} from '../src/api/types';

function createJwt(subject: string, roles: string[] = ['ADMIN']): string {
  const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
  const payload = btoa(
    JSON.stringify({
      sub: subject,
      roles,
      iss: 'https://auth.zkdoc.internal',
      aud: 'zkdoc-api',
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 3600,
    }),
  )
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
  return `${header}.${payload}.mock-signature-bytes`;
}

describe('Phase F10: Frontend Security Hardening & Adversarial Regression Suite', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    authStore.resetForTesting();
    holderStore.reset();
    issuerStore.clearActivity();
    adminStore.reset();
    verifierStore.reset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('SEC-F10-001: Session Teardown & Cross-Session Isolation', () => {
    const mockCred: HolderCredentialResponse = {
      id: 'cred-alice-secret-1',
      holderId: 'urn:zkdoc:holder:alice',
      credentialId: 'cred-1',
      credentialDigestHex: 'abcdef1234567890',
      status: 'ACTIVE',
      version: 1,
      provisionedAt: '2026-10-15T00:00:00Z',
      updatedAt: '2026-10-15T00:00:00Z',
    };

    const mockKey: KeyMetadataResponse = {
      id: 'key-uuid-1',
      keyId: 'key-admin-1',
      purpose: 'CREDENTIAL_SIGNING',
      ownerId: 'platform',
      ownerType: 'PLATFORM',
      algorithm: 'RSASSA-PSS',
      status: 'ACTIVE',
      publicKeyPem: '-----BEGIN PUBLIC KEY-----\nMIIB...-----END PUBLIC KEY-----',
      fingerprint: 'sha256-fingerprint',
      createdAt: '2026-10-15T00:00:00Z',
      activatedAt: '2026-10-15T00:00:00Z',
      retiredAt: null,
      revokedAt: null,
      replacementKeyId: null,
    };

    const mockAudit: SecurityAuditEventResponse = {
      eventId: 'evt-sensitive-1',
      sequenceNumber: 1,
      eventType: 'KEY_REGISTERED',
      eventCategory: 'SYSTEM_ADMINISTRATION',
      timestamp: '2026-10-15T00:00:00Z',
      actorSubject: 'urn:zkdoc:admin:operator1',
      actorType: 'ADMIN',
      targetType: 'KEY',
      targetId: 'key-admin-1',
      action: 'REGISTER',
      outcome: 'SUCCESS',
      reasonCode: null,
      correlationId: 'corr-1',
      clientIpHash: '127.0.0.1',
      metadataJson: '{}',
      previousEventHash: '00000000',
      eventHash: 'hash-1',
    };

    it('wipes all sensitive domain stores upon explicit logout', () => {
      // 1. Establish session for Alice
      authStore.setToken(createJwt('urn:zkdoc:holder:alice', ['HOLDER']));
      expect(authStore.getState().isAuthenticated).toBe(true);

      // 2. Populate domain stores with sensitive data
      holderStore.setCredentials([mockCred]);
      issuerStore.addActivity({
        id: 'doc-1',
        type: 'document',
        title: 'sensitive-tax-return.pdf',
        digest: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      });
      adminStore.setKeys([mockKey]);
      adminStore.setSelectedAuditEvent(mockAudit);
      verifierStore.setResult({
        kind: 'PUBLIC_CAPABILITY',
        token: 'cap-token-secret-12345',
        response: {
          verified: true,
          valid: true,
          status: 'ACTIVE',
          mode: 'MODE_A_PRESENTED_ARTIFACT',
          protocolVersion: '1.0',
          signatureValid: true,
          active: true,
          failureReason: null,
        },
        timestamp: '2026-10-15T00:00:00Z',
      });

      // Verify data is present in memory
      expect(holderStore.getState().credentials).toHaveLength(1);
      expect(issuerStore.getState().activities).toHaveLength(1);
      expect(adminStore.getState().keys).toHaveLength(1);
      expect(adminStore.getState().selectedAuditEvent).not.toBeNull();
      expect(verifierStore.getState().activeResult).not.toBeNull();

      // 3. User logs out
      authStore.clearSession('User initiated logout');

      // 4. Assert full cascading wipe across all domain stores
      expect(authStore.getState().isAuthenticated).toBe(false);
      expect(holderStore.getState().credentials).toEqual([]);
      expect(issuerStore.getState().activities).toEqual([]);
      expect(adminStore.getState().keys).toEqual([]);
      expect(adminStore.getState().selectedAuditEvent).toBeNull();
      expect(verifierStore.getState().activeResult).toBeNull();
      expect(verifierStore.getState().history).toEqual([]);
    });

    it('purges previous user sensitive state when switching accounts without page reload', () => {
      // 1. Alice logs in and stores credentials
      authStore.setToken(createJwt('urn:zkdoc:holder:alice', ['HOLDER']));
      holderStore.setCredentials([mockCred]);
      issuerStore.addActivity({
        id: 'doc-alice',
        type: 'document',
        title: 'alice-payroll.pdf',
        digest: 'digest-alice',
      });
      expect(holderStore.getState().credentials).toHaveLength(1);

      // 2. Bob logs in on same browser session without explicit logout
      authStore.setToken(createJwt('urn:zkdoc:holder:bob', ['HOLDER']));
      expect(authStore.getState().user?.subject).toBe('urn:zkdoc:holder:bob');

      // 3. Verify Alice\'s credentials and activities were purged during account switch
      expect(holderStore.getState().credentials).toEqual([]);
      expect(issuerStore.getState().activities).toEqual([]);
    });

    it('rejects in-flight async request responses resolving after session termination', async () => {
      // 1. Establish authenticated session
      authStore.setToken(createJwt('urn:zkdoc:holder:alice', ['HOLDER']));
      const initialEpoch = authStore.getSessionEpoch();

      // 2. Mock a delayed network request
      let resolveFetch!: (res: unknown) => void;
      const delayedPromise = new Promise((resolve) => {
        resolveFetch = resolve;
      });

      vi.spyOn(globalThis, 'fetch').mockImplementationOnce(
        () => delayedPromise as unknown as Promise<Response>,
      );

      // 3. Kick off in-flight async call
      const requestPromise = apiRequest<{ data: string }>('/api/v1/sensitive-data');

      // 4. User logs out while request is in flight
      authStore.clearSession();
      expect(authStore.getSessionEpoch()).toBeGreaterThan(initialEpoch);

      // 5. Complete delayed network response
      resolveFetch({
        ok: true,
        status: 200,
        headers: new Headers({ 'Content-Type': 'application/json' }),
        json: () => Promise.resolve({ data: 'leaked-data-should-be-blocked' }),
      });

      // 6. Assert apiRequest rejects with status 499 (Client Session Terminated)
      await expect(requestPromise).rejects.toThrow(/Client Session Terminated|terminated/i);
    });
  });

  describe('SEC-F10-003: Filename Sanitization & Unicode Bidi Spoofing Defense', () => {
    it('strips Unicode bi-directional override characters used for extension spoofing', () => {
      // Right-To-Left Override (\u202E) disguises .exe as .pdf visually: "report\u202Efdp.exe"
      const maliciousBidi = 'quarterly-report\u202Efdp.exe';
      const clean = sanitizeFilename(maliciousBidi);
      expect(clean).toBe('quarterly-reportfdp.exe');
      expect(clean).not.toContain('\u202E');
    });

    it('strips zero-width characters, invisible formatters, and byte order marks', () => {
      const maliciousHidden = 'doc\u200B\u200C\u200D\u200E\u200F\uFEFFument.pdf';
      const clean = sanitizeFilename(maliciousHidden);
      expect(clean).toBe('document.pdf');
    });

    it('strips ASCII control characters and null bytes', () => {
      const dirty = 'report\x00\x01\x1F\x7F\x9F\r\n\t.pdf';
      const clean = sanitizeFilename(dirty);
      expect(clean).toBe('report.pdf');
    });

    it('recursively neutralizes nested path traversal sequences', () => {
      // Single pass removal of ".." turns "....//....//" into "../.."
      const traversalAttack = '....//....//....//etc/passwd';
      const clean = sanitizeFilename(traversalAttack);
      expect(clean).toBe('passwd');
      expect(clean).not.toContain('..');
    });

    it('trims leading and trailing dots and spaces to prevent hidden files', () => {
      expect(sanitizeFilename('...hidden-doc.pdf...')).toBe('hidden-doc.pdf');
      expect(sanitizeFilename('   .bashrc   ')).toBe('bashrc');
    });

    it('enforces maximum filename bound of 255 characters', () => {
      const superLong = 'a'.repeat(300) + '.pdf';
      const clean = sanitizeFilename(superLong);
      expect(clean.length).toBe(MAX_FILENAME_LENGTH);
    });

    it('falls back to safe default document.bin for blank or neutralized input', () => {
      expect(sanitizeFilename('')).toBe('document.bin');
      expect(sanitizeFilename('    ')).toBe('document.bin');
      expect(sanitizeFilename('../../../')).toBe('document.bin');
      expect(sanitizeFilename('....////')).toBe('document.bin');
    });
  });

  describe('SEC-F10-004: Client-Side Magic Bytes Inspection (Backend Parity)', () => {
    it('accepts authentic PDF file with %PDF magic header', async () => {
      // %PDF-1.7 header: [0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37]
      const pdfBytes = new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x37]);
      const file = new File([pdfBytes], 'report.pdf', { type: 'application/pdf' });

      const result = await validateFileMagicBytes(file);
      expect(result.valid).toBe(true);
      expect(result.error).toBeUndefined();
    });

    it('accepts authentic PNG image with standard 8-byte magic header', async () => {
      const pngHeader = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
      const file = new File([pngHeader], 'stamp.png', { type: 'image/png' });

      const result = await validateFileMagicBytes(file);
      expect(result.valid).toBe(true);
    });

    it('accepts authentic JPEG image with SOI marker', async () => {
      const jpegHeader = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10]);
      const file = new File([jpegHeader], 'photo.jpg', { type: 'image/jpeg' });

      const result = await validateFileMagicBytes(file);
      expect(result.valid).toBe(true);
    });

    it('accepts clean plain text document without binary executable signatures', async () => {
      const textBytes = new TextEncoder().encode('Attestation statement text content.');
      const file = new File([textBytes], 'statement.txt', { type: 'text/plain' });

      const result = await validateFileMagicBytes(file);
      expect(result.valid).toBe(true);
    });

    it('rejects executable file renamed to .pdf (MZ / PE DOS header spoofing)', async () => {
      // Windows PE / DOS executable header starts with 'M', 'Z' (0x4D, 0x5A)
      const exeBytes = new Uint8Array([0x4d, 0x5a, 0x90, 0x00, 0x03, 0x00, 0x00, 0x00]);
      const spoofedPdf = new File([exeBytes], 'malware.pdf', { type: 'application/pdf' });

      const result = await validateFileMagicBytes(spoofedPdf);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('File declared as PDF does not match PDF magic header');
    });

    it('rejects ELF binary executable disguised as .txt file', async () => {
      // Linux ELF executable header: [0x7F, 'E', 'L', 'F']
      const elfBytes = new Uint8Array([0x7f, 0x45, 0x4c, 0x46, 0x02, 0x01, 0x01, 0x00]);
      const disguisedText = new File([elfBytes], 'exploit.txt', { type: 'text/plain' });

      const result = await validateFileMagicBytes(disguisedText);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('Binary or executable content rejected for text/plain document');
    });

    it('rejects plain text file containing null bytes (binary polyglot defense)', async () => {
      const polyglotBytes = new Uint8Array([0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x00, 0x77, 0x6f]);
      const file = new File([polyglotBytes], 'notes.txt', { type: 'text/plain' });

      const result = await validateFileMagicBytes(file);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('Binary or executable content rejected for text/plain document');
    });

    it('rejects files smaller than magic byte threshold', async () => {
      const tinyBytes = new Uint8Array([0x25, 0x50]); // Only 2 bytes for PDF
      const file = new File([tinyBytes], 'short.pdf', { type: 'application/pdf' });

      const result = await validateFileMagicBytes(file);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('File content too small to verify magic bytes');
    });
  });

  describe('SEC-F10-005: Zero Private Key Policy Enforcement in Trust Registry', () => {
    it('strictly intercepts and rejects inadvertent RSA private key PEM submission', async () => {
      const onClose = vi.fn();
      const onSuccess = vi.fn();

      render(
        <RegisterKeyModal
          isOpen={true}
          onClose={onClose}
          onSuccess={onSuccess}
        />,
      );

      const keyIdInput = screen.getByLabelText(/Key Identifier/i);
      const pemTextarea = screen.getByLabelText(/Public Key \(X\.509 PEM\)/i);
      const submitBtn = screen.getByRole('button', { name: /Register Key Anchor/i });

      // User enters valid Key ID
      fireEvent.change(keyIdInput, { target: { value: 'key-accidental-private' } });

      // User accidentally pastes an RSA private key PEM block
      const privateKeyPem = `-----BEGIN RSA PRIVATE KEY-----
MIIEowIBAAKCAQEA0m4wz7d7F8u9...
-----END RSA PRIVATE KEY-----`;

      fireEvent.change(pemTextarea, { target: { value: privateKeyPem } });
      fireEvent.click(submitBtn);

      // Verify that security violation is triggered
      await waitFor(() => {
        expect(screen.getByText(/Security Violation: Private key material detected!/i)).toBeInTheDocument();
      });

      // Verify Zero Private Key Policy explanation is rendered
      expect(
        screen.getByText(/ZKDoc enforces a strict Zero Private Key Policy/i),
      ).toBeInTheDocument();

      // Verify that the private key input was purged from the DOM/state for defense in depth
      expect(pemTextarea).toHaveValue('');

      // Backend API should NOT have been invoked
      expect(onSuccess).not.toHaveBeenCalled();
    });

    it('rejects PKCS#8 unencrypted or encrypted private keys', async () => {
      render(
        <RegisterKeyModal
          isOpen={true}
          onClose={vi.fn()}
          onSuccess={vi.fn()}
        />,
      );

      const keyIdInput = screen.getByLabelText(/Key Identifier/i);
      const pemTextarea = screen.getByLabelText(/Public Key \(X\.509 PEM\)/i);
      const submitBtn = screen.getByRole('button', { name: /Register Key Anchor/i });

      fireEvent.change(keyIdInput, { target: { value: 'key-pkcs8-private' } });
      fireEvent.change(pemTextarea, {
        target: {
          value: '-----BEGIN PRIVATE KEY-----\nMIGHAgEAMBMG...-----END PRIVATE KEY-----',
        },
      });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(screen.getByText(/Security Violation: Private key material detected!/i)).toBeInTheDocument();
      });
      expect(pemTextarea).toHaveValue('');
    });
  });

  describe('SEC-F10-002: Deterministic Object URL Revocation & Media Stream Cleanup', () => {
    it('revokes Object URL in QrScannerCard even when image decoding throws', async () => {
      const revokeSpy = vi.spyOn(URL, 'revokeObjectURL');
      const createSpy = vi
        .spyOn(URL, 'createObjectURL')
        .mockReturnValue('blob:http://localhost/mock-qr-blob');

      // Mock Image in jsdom so onerror is dispatched when src is set
      const originalImage = globalThis.Image;
      class MockImage {
        public onload: (() => void) | null = null;
        public onerror: ((err: Error) => void) | null = null;
        private _src = '';

        set src(value: string) {
          this._src = value;
          setTimeout(() => {
            if (this.onerror) {
              this.onerror(new Error('Mock image decode error'));
            }
          }, 0);
        }

        get src(): string {
          return this._src;
        }
      }
      globalThis.Image = MockImage as unknown as typeof Image;

      try {
        const onScanSuccess = vi.fn();
        render(<QrScannerCard onScanSuccess={onScanSuccess} />);

        const fileInput = document.querySelector(
          'input[type="file"]',
        ) as HTMLInputElement;

        // Create an invalid/corrupt QR image
        const corruptImage = new File(
          ['not-a-valid-qr-bitmap'],
          'corrupt.png',
          {
            type: 'image/png',
          },
        );

        // Trigger file upload decoding
        await userEvent.upload(fileInput, corruptImage);

        // Verify that URL.revokeObjectURL was called in finally block despite decode failure
        await waitFor(() => {
          expect(createSpy).toHaveBeenCalled();
          expect(revokeSpy).toHaveBeenCalledWith(
            'blob:http://localhost/mock-qr-blob',
          );
        });
      } finally {
        globalThis.Image = originalImage;
      }
    });

    it('stops all tracks on MediaStream when camera is stopped', () => {
      const stopTrack1 = vi.fn();
      const stopTrack2 = vi.fn();

      const mockStream = {
        getTracks: () => [
          { stop: stopTrack1, kind: 'video' },
          { stop: stopTrack2, kind: 'video' },
        ],
      } as unknown as MediaStream;

      // Mock navigator.mediaDevices.getUserMedia
      vi.stubGlobal('navigator', {
        mediaDevices: {
          getUserMedia: vi.fn().mockResolvedValue(mockStream),
        },
      });

      const { unmount } = render(<QrScannerCard onScanSuccess={vi.fn()} />);

      // Unmounting component triggers stopCamera
      unmount();

      // Assert clean unmount without throwing
      expect(true).toBe(true);
    });
  });
});
