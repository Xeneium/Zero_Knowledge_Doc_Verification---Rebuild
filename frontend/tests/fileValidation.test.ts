import { describe, it, expect } from 'vitest';
import {
  validateDocumentFile,
  formatFileSize,
  sanitizeFilename,
  getFileExtension,
  MAX_FILE_SIZE_BYTES,
  MAX_FILENAME_LENGTH,
  ALLOWED_EXTENSIONS,
  ALLOWED_MIME_TYPES,
} from '../src/lib/utils/fileValidation';

describe('fileValidation Utilities', () => {
  it('exports authoritative constants matching backend constraints', () => {
    expect(MAX_FILE_SIZE_BYTES).toBe(25 * 1024 * 1024);
    expect(MAX_FILENAME_LENGTH).toBe(255);
    expect(ALLOWED_EXTENSIONS).toContain('.pdf');
    expect(ALLOWED_EXTENSIONS).toContain('.png');
    expect(ALLOWED_EXTENSIONS).toContain('.jpg');
    expect(ALLOWED_EXTENSIONS).toContain('.jpeg');
    expect(ALLOWED_EXTENSIONS).toContain('.txt');
    expect(ALLOWED_MIME_TYPES).toContain('application/pdf');
    expect(ALLOWED_MIME_TYPES).toContain('image/png');
    expect(ALLOWED_MIME_TYPES).toContain('image/jpeg');
    expect(ALLOWED_MIME_TYPES).toContain('text/plain');
  });

  describe('validateDocumentFile', () => {
    it('approves a valid PDF file under 25 MB', () => {
      const file = new File(['%PDF-1.4 test content'], 'certificate.pdf', {
        type: 'application/pdf',
      });
      const result = validateDocumentFile(file);
      expect(result.valid).toBe(true);
      expect(result.error).toBeUndefined();
    });

    it('approves valid PNG and JPEG images', () => {
      const pngFile = new File(['png-data'], 'scan.png', { type: 'image/png' });
      const jpgFile = new File(['jpeg-data'], 'photo.jpg', {
        type: 'image/jpeg',
      });
      expect(validateDocumentFile(pngFile).valid).toBe(true);
      expect(validateDocumentFile(jpgFile).valid).toBe(true);
    });

    it('approves plain text documents', () => {
      const txtFile = new File(['statement text'], 'statement.txt', {
        type: 'text/plain',
      });
      expect(validateDocumentFile(txtFile).valid).toBe(true);
    });

    it('rejects files exceeding 25 MB limit', () => {
      const largeSize = 25 * 1024 * 1024 + 1; // 25 MB + 1 byte
      const largeFile = new File(['x'], 'huge.pdf', {
        type: 'application/pdf',
      });
      Object.defineProperty(largeFile, 'size', { value: largeSize });

      const result = validateDocumentFile(largeFile);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('exceeds maximum limit of 25 MB');
    });

    it('rejects unsupported extensions even if MIME type spoofed', () => {
      const evilFile = new File(['bad'], 'script.exe', {
        type: 'application/pdf',
      });
      const result = validateDocumentFile(evilFile);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('Unsupported format');
    });

    it('rejects unsupported MIME types even if extension is spoofed', () => {
      const evilFile = new File(['bad'], 'exploit.pdf', {
        type: 'application/x-msdownload',
      });
      const result = validateDocumentFile(evilFile);
      expect(result.valid).toBe(false);
      expect(result.error).toContain('Unsupported format');
    });

    it('rejects filenames exceeding 255 characters', () => {
      const longName = 'a'.repeat(252) + '.pdf'; // 256 chars total
      const file = new File(['data'], longName, { type: 'application/pdf' });
      const result = validateDocumentFile(file);
      expect(result.valid).toBe(false);
      expect(result.error).toContain(
        'Filename exceeds maximum length of 255 characters',
      );
    });

    it('rejects empty or null files', () => {
      expect(validateDocumentFile(null).valid).toBe(false);
      expect(validateDocumentFile(undefined).valid).toBe(false);
      const emptyFile = new File([], 'empty.pdf', { type: 'application/pdf' });
      const emptyResult = validateDocumentFile(emptyFile);
      expect(emptyResult.valid).toBe(false);
      expect(emptyResult.error).toContain('0 bytes');
    });
  });

  describe('getFileExtension', () => {
    it('correctly extracts lowercase extension', () => {
      expect(getFileExtension('document.PDF')).toBe('.pdf');
      expect(getFileExtension('archive.tar.gz')).toBe('.gz');
      expect(getFileExtension('noextension')).toBe('');
    });
  });

  describe('formatFileSize', () => {
    it('formats 0 bytes correctly', () => {
      expect(formatFileSize(0)).toBe('0 B');
    });

    it('formats bytes, kilobytes, and megabytes', () => {
      expect(formatFileSize(500)).toBe('500 B');
      expect(formatFileSize(1024)).toBe('1.0 KB');
      expect(formatFileSize(1536)).toBe('1.5 KB');
      expect(formatFileSize(1024 * 1024)).toBe('1.00 MB');
      expect(formatFileSize(25 * 1024 * 1024)).toBe('25.00 MB');
    });
  });

  describe('sanitizeFilename', () => {
    it('strips directory traversal attempts', () => {
      expect(sanitizeFilename('../../etc/passwd.pdf')).toBe('passwd.pdf');
      expect(sanitizeFilename('..\\..\\windows\\system32.pdf')).toBe(
        'system32.pdf',
      );
    });

    it('cleans control characters and null bytes', () => {
      expect(sanitizeFilename('my doc\0 (1).pdf')).toBe('my doc (1).pdf');
      expect(sanitizeFilename('')).toBe('document.bin');
    });
  });
});
