import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { DocumentUploadCard } from '../src/features/issuer/components/DocumentUploadCard';
import { DocumentUploadPage } from '../src/routes/DocumentUploadPage';
import { authStore } from '../src/state/authStore';
import { issuerStore } from '../src/state/issuerStore';
import { issuerApi } from '../src/api/issuerApi';
import type { DocumentUploadResponse } from '../src/api/types';

describe('Phase 6: Document Upload Experience Suite', () => {
  beforeEach(() => {
    authStore.clearSession();
    issuerStore.clearActivity();
    window.sessionStorage.clear();
    window.localStorage.clear();
    vi.restoreAllMocks();

    // Authenticate as test issuer authority
    const payload = btoa(
      JSON.stringify({
        sub: 'urn:zkdoc:issuer:registrar',
        roles: ['ROLE_ISSUER'],
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    authStore.setToken(`header.${payload}.sig`);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('DocumentUploadPage Routing & Structure', () => {
    it('renders dedicated document registration page with architectural explanation', () => {
      render(
        <MemoryRouter initialEntries={['/upload']}>
          <Routes>
            <Route path="/upload" element={<DocumentUploadPage />} />
          </Routes>
        </MemoryRouter>,
      );

      expect(
        screen.getByRole('heading', {
          name: /document registration & integrity anchoring/i,
        }),
      ).toBeInTheDocument();
      expect(screen.getByText(/ROLE_ISSUER ACTIVE/i)).toBeInTheDocument();
      expect(
        screen.getByText(/No Embedded QR Required/i),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/1\. SHA-256 Digest/i),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/2\. RSA-PSS Signature/i),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/3\. Public Capability/i),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/4\. Minimal Disclosure/i),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/25 MB bound/i),
      ).toBeInTheDocument();
    });
  });

  describe('DocumentUploadCard File Selection & Validation Lifecycle', () => {
    it('renders empty upload dropzone with 25 MB max bound and accessible keyboard controls', () => {
      render(
        <MemoryRouter>
          <DocumentUploadCard />
        </MemoryRouter>,
      );

      const dropzone = screen.getByRole('button', {
        name: /document file upload dropzone/i,
      });
      expect(dropzone).toHaveAttribute('tabIndex', '0');
      expect(screen.getByText(/25 MB Max Bound/i)).toBeInTheDocument();
      expect(
        screen.getByText(/supported formats: pdf, png, jpeg, plain text/i),
      ).toBeInTheDocument();
    });

    it('handles file selection, displays summary, and allows removing selected file', async () => {
      const user = userEvent.setup();
      render(
        <MemoryRouter>
          <DocumentUploadCard />
        </MemoryRouter>,
      );

      const file = new File(['valid degree content'], 'official_degree.pdf', {
        type: 'application/pdf',
      });
      const input = document.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file);

      expect(screen.getByText('official_degree.pdf')).toBeInTheDocument();
      expect(screen.getByText(/application\/pdf/i)).toBeInTheDocument();

      const removeBtn = screen.getByRole('button', {
        name: /remove selected file/i,
      });
      await user.click(removeBtn);

      expect(screen.queryByText('official_degree.pdf')).not.toBeInTheDocument();
      expect(
        screen.getByText(/click to select or drag and drop document artifact/i),
      ).toBeInTheDocument();
    });

    it('supports replacing selected file with a new file', async () => {
      const user = userEvent.setup();
      render(
        <MemoryRouter>
          <DocumentUploadCard />
        </MemoryRouter>,
      );

      const file1 = new File(['first'], 'first.pdf', { type: 'application/pdf' });
      const file2 = new File(['second'], 'second.png', { type: 'image/png' });
      const input = document.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, file1);
      expect(screen.getByText('first.pdf')).toBeInTheDocument();

      const replaceBtn = screen.getByRole('button', {
        name: /replace selected file/i,
      });
      expect(replaceBtn).toBeInTheDocument();

      await user.upload(input, file2);
      expect(screen.getByText('second.png')).toBeInTheDocument();
      expect(screen.queryByText('first.pdf')).not.toBeInTheDocument();
    });

    it('rejects empty (0 byte) file selection', async () => {
      const user = userEvent.setup();
      render(
        <MemoryRouter>
          <DocumentUploadCard />
        </MemoryRouter>,
      );

      const emptyFile = new File([], 'empty.pdf', { type: 'application/pdf' });
      const input = document.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, emptyFile);

      expect(
        await screen.findByText(/upload file must not be empty \(0 bytes\)/i),
      ).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: /upload & secure document/i }),
      ).toBeDisabled();
    });

    it('rejects files exceeding 25 MB bound', async () => {
      const user = userEvent.setup();
      render(
        <MemoryRouter>
          <DocumentUploadCard />
        </MemoryRouter>,
      );

      const oversizeFile = new File(['huge'], 'huge.pdf', {
        type: 'application/pdf',
      });
      Object.defineProperty(oversizeFile, 'size', {
        value: 26 * 1024 * 1024,
      });
      const input = document.querySelector('input[type="file"]') as HTMLInputElement;

      await user.upload(input, oversizeFile);

      expect(
        await screen.findByText(/exceeds maximum limit of 25 MB/i),
      ).toBeInTheDocument();
    });

    it('rejects unsupported extensions', async () => {
      render(
        <MemoryRouter>
          <DocumentUploadCard />
        </MemoryRouter>,
      );

      const scriptFile = new File(['binary'], 'malware.sh', {
        type: 'application/x-sh',
      });
      const input = document.querySelector('input[type="file"]') as HTMLInputElement;

      fireEvent.change(input, { target: { files: [scriptFile] } });

      expect(
        await screen.findByText(/unsupported format/i),
      ).toBeInTheDocument();
    });
  });

  describe('DocumentUploadCard Upload Submission & Backend Integration', () => {
    it('submits document via multipart/form-data to /api/documents and renders full authoritative response', async () => {
      const user = userEvent.setup();
      const mockResponse = {
        id: '987fcdeb-51a2-43d7-9012-345678901234',
        digest: '4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b',
        publicToken: 'c0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ff',
        verificationUrl:
          'https://zkdoc.example.com/verify?token=c0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ffeec0ff',
      };
      const uploadSpy = vi
        .spyOn(issuerApi, 'uploadDocument')
        .mockResolvedValueOnce(mockResponse);

      const mockQrBlob = new Blob(['png-magic-bytes'], { type: 'image/png' });
      vi.spyOn(issuerApi, 'getDocumentQrBlob').mockResolvedValueOnce(mockQrBlob);

      const onSuccess = vi.fn();
      render(
        <MemoryRouter>
          <DocumentUploadCard onSuccess={onSuccess} />
        </MemoryRouter>,
      );

      const file = new File(['valid degree content'], 'university_diploma.pdf', {
        type: 'application/pdf',
      });
      const input = document.querySelector('input[type="file"]') as HTMLInputElement;
      await user.upload(input, file);

      const submitBtn = screen.getByRole('button', {
        name: /upload & secure document/i,
      });
      await user.click(submitBtn);

      expect(uploadSpy).toHaveBeenCalledWith(file);

      await waitFor(() => {
        expect(
          screen.getByText(/document successfully uploaded & secured/i),
        ).toBeInTheDocument();
      });

      // Verify non-sensitive label
      expect(screen.getByText(/university_diploma\.pdf/i)).toBeInTheDocument();

      // Verify UUID
      expect(screen.getByText(mockResponse.id)).toBeInTheDocument();

      // Verify SHA-256 Digest is formatted
      expect(
        screen.getByTitle(mockResponse.digest),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/4a5e1e4b.*eda33b/),
      ).toBeInTheDocument();

      // Verify Public Token
      expect(screen.getByText(mockResponse.publicToken)).toBeInTheDocument();

      // Verify Direct Link
      expect(
        screen.getByRole('link', { name: mockResponse.verificationUrl }),
      ).toHaveAttribute('href', mockResponse.verificationUrl);

      // Verify Workflow Separation Notice
      expect(
        screen.getByText(/the original document did not require an embedded qr code/i),
      ).toBeInTheDocument();

      // Verify Public Verification Portal Link
      const publicVerifyLink = screen.getByRole('link', {
        name: /open in public verification/i,
      });
      expect(publicVerifyLink).toHaveAttribute(
        'href',
        `/verify?token=${mockResponse.publicToken}`,
      );

      expect(onSuccess).toHaveBeenCalledWith(mockResponse);
    });

    it('renders accessible progress bar during in-flight upload and prevents duplicate submission', async () => {
      const user = userEvent.setup();
      let resolveUpload: (val: DocumentUploadResponse) => void = () => {};
      const pendingPromise = new Promise<DocumentUploadResponse>((resolve) => {
        resolveUpload = resolve;
      });
      vi.spyOn(issuerApi, 'uploadDocument').mockReturnValueOnce(
        pendingPromise,
      );

      render(
        <MemoryRouter>
          <DocumentUploadCard />
        </MemoryRouter>,
      );

      const file = new File(['sample'], 'transcript.pdf', {
        type: 'application/pdf',
      });
      const input = document.querySelector('input[type="file"]') as HTMLInputElement;
      await user.upload(input, file);

      const submitBtn = screen.getByRole('button', {
        name: /upload & secure document/i,
      });
      await user.click(submitBtn);

      // Verify progress bar is rendered
      expect(
        screen.getByRole('progressbar', { name: /document upload progress/i }),
      ).toBeInTheDocument();
      expect(screen.getByText(/uploading and computing cryptographic/i)).toBeInTheDocument();

      // Button is disabled during submission
      expect(submitBtn).toBeDisabled();

      // Resolve upload
      resolveUpload({
        id: 'doc-uuid-1234',
        digest: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
      });

      await waitFor(() => {
        expect(
          screen.getByText(/document successfully uploaded & secured/i),
        ).toBeInTheDocument();
      });
    });

    it('preserves selected file when backend returns an error so user can retry', async () => {
      const user = userEvent.setup();
      vi.spyOn(issuerApi, 'uploadDocument').mockRejectedValueOnce(
        new Error('Network error or server unavailable (503)'),
      );

      render(
        <MemoryRouter>
          <DocumentUploadCard />
        </MemoryRouter>,
      );

      const file = new File(['retry data'], 'contract.pdf', {
        type: 'application/pdf',
      });
      const input = document.querySelector('input[type="file"]') as HTMLInputElement;
      await user.upload(input, file);

      const submitBtn = screen.getByRole('button', {
        name: /upload & secure document/i,
      });
      await user.click(submitBtn);

      // Verify error alert
      expect(
        await screen.findByText(/network error or server unavailable \(503\)/i),
      ).toBeInTheDocument();

      // File is still selected and preserved
      expect(screen.getByText('contract.pdf')).toBeInTheDocument();

      // Submit button is active again for retry
      expect(
        screen.getByRole('button', { name: /upload & secure document/i }),
      ).not.toBeDisabled();
    });
  });

  describe('Security & Sensitive Data Hygiene', () => {
    it('renders malicious filename with HTML characters safely as text without XSS injection', async () => {
      const user = userEvent.setup();
      render(
        <MemoryRouter>
          <DocumentUploadCard />
        </MemoryRouter>,
      );

      const xssFileName = '<script>alert("xss")</script>.pdf';
      const file = new File(['content'], xssFileName, {
        type: 'application/pdf',
      });
      const input = document.querySelector('input[type="file"]') as HTMLInputElement;
      await user.upload(input, file);

      expect(screen.getByText(xssFileName)).toBeInTheDocument();
      expect(document.querySelector('script[src*="alert"]')).toBeNull();
    });

    it('does not write uploaded document bytes to browser localStorage or sessionStorage', async () => {
      const user = userEvent.setup();
      vi.spyOn(issuerApi, 'uploadDocument').mockResolvedValueOnce({
        id: 'doc-1234',
        digest: 'abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890',
      });

      render(
        <MemoryRouter>
          <DocumentUploadCard />
        </MemoryRouter>,
      );

      const secretContent = 'SUPER_SECRET_PATIENT_MEDICAL_RECORDS_DATA_DO_NOT_STORE';
      const file = new File([secretContent], 'records.pdf', {
        type: 'application/pdf',
      });
      const input = document.querySelector('input[type="file"]') as HTMLInputElement;
      await user.upload(input, file);

      const submitBtn = screen.getByRole('button', {
        name: /upload & secure document/i,
      });
      await user.click(submitBtn);

      await waitFor(() => {
        expect(screen.getByText('doc-1234')).toBeInTheDocument();
      });

      // Assert no sensitive document content in storage
      for (let i = 0; i < window.sessionStorage.length; i++) {
        const key = window.sessionStorage.key(i)!;
        expect(window.sessionStorage.getItem(key)).not.toContain(secretContent);
      }
      for (let i = 0; i < window.localStorage.length; i++) {
        const key = window.localStorage.key(i)!;
        expect(window.localStorage.getItem(key)).not.toContain(secretContent);
      }
    });
  });
});
