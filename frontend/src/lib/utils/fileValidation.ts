/**
 * File validation utilities strictly matching backend DocumentContentValidator.java constraints:
 * - Max file size: 25 MB (26,214,400 bytes)
 * - Supported types: PDF, PNG, JPEG, Plain Text
 * - Filename length: <= 255 characters
 */

export const MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024; // 25 MB
export const MAX_FILENAME_LENGTH = 255;

export const ALLOWED_MIME_TYPES = [
  'application/pdf',
  'image/png',
  'image/jpeg',
  'text/plain',
] as const;

export const ALLOWED_EXTENSIONS = [
  '.pdf',
  '.png',
  '.jpg',
  '.jpeg',
  '.txt',
] as const;

export interface FileValidationResult {
  valid: boolean;
  error?: string;
}

/**
 * Validates a document file against backend constraints before upload.
 */
export function validateDocumentFile(
  file: File | null | undefined,
): FileValidationResult {
  if (!file) {
    return { valid: false, error: 'Please select a file to upload.' };
  }

  if (file.size === 0) {
    return {
      valid: false,
      error: 'Upload file must not be empty (0 bytes).',
    };
  }

  if (file.size > MAX_FILE_SIZE_BYTES) {
    return {
      valid: false,
      error: `File size (${formatFileSize(file.size)}) exceeds maximum limit of 25 MB.`,
    };
  }

  if (file.name.length > MAX_FILENAME_LENGTH) {
    return {
      valid: false,
      error: `Filename exceeds maximum length of ${MAX_FILENAME_LENGTH} characters.`,
    };
  }

  const extension = getFileExtension(file.name);
  const hasValidExtension = (ALLOWED_EXTENSIONS as readonly string[]).includes(
    extension,
  );

  // If MIME type is reported by browser, check it; otherwise rely on extension
  const hasValidMime =
    !file.type ||
    (ALLOWED_MIME_TYPES as readonly string[]).includes(file.type.toLowerCase());

  if (!hasValidExtension || !hasValidMime) {
    return {
      valid: false,
      error: `Unsupported format. Allowed formats: PDF (.pdf), PNG (.png), JPEG (.jpg, .jpeg), Plain Text (.txt).`,
    };
  }

  return { valid: true };
}

/**
 * Extracts lowercase file extension including dot (e.g., '.pdf').
 */
export function getFileExtension(filename: string): string {
  const lastDot = filename.lastIndexOf('.');
  if (lastDot === -1) return '';
  return filename.substring(lastDot).toLowerCase();
}

/**
 * Formats byte count into human-readable string (KB, MB).
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

const PDF_MAGIC = [0x25, 0x50, 0x44, 0x46]; // %PDF
const PNG_MAGIC = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
const JPEG_MAGIC = [0xff, 0xd8, 0xff];

function matchesPrefix(data: Uint8Array, prefix: number[]): boolean {
  if (data.length < prefix.length) return false;
  for (let i = 0; i < prefix.length; i++) {
    if (data[i] !== prefix[i]) return false;
  }
  return true;
}

function containsNullBytes(data: Uint8Array): boolean {
  for (let i = 0; i < data.length; i++) {
    if (data[i] === 0) return true;
  }
  return false;
}

/**
 * Inspects client-side file magic bytes to verify content matches declared type,
 * strictly matching backend DocumentContentValidator.java constraints.
 */
export async function validateFileMagicBytes(
  file: File | null | undefined,
): Promise<FileValidationResult> {
  const basic = validateDocumentFile(file);
  if (!basic.valid || !file) {
    return basic;
  }

  try {
    const slice = file.slice(0, 16);
    const buffer = await slice.arrayBuffer();
    const header = new Uint8Array(buffer);

    const ext = getFileExtension(file.name);
    const mime = (file.type || '').toLowerCase();

    const isPdf = ext === '.pdf' || mime === 'application/pdf';
    const isPng = ext === '.png' || mime === 'image/png';
    const isJpeg = ext === '.jpg' || ext === '.jpeg' || mime === 'image/jpeg';
    const isText = ext === '.txt' || mime === 'text/plain';

    if (isText) {
      if (
        header.length >= 4 &&
        ((header[0] === 0x7f &&
          header[1] === 0x45 &&
          header[2] === 0x4c &&
          header[3] === 0x46) ||
          (header[0] === 0x4d && header[1] === 0x5a))
      ) {
        return {
          valid: false,
          error:
            'Binary or executable content rejected for text/plain document.',
        };
      }
      if (containsNullBytes(header)) {
        return {
          valid: false,
          error:
            'Binary or executable content rejected for text/plain document.',
        };
      }
      return { valid: true };
    }

    if (header.length < 3) {
      return {
        valid: false,
        error: 'File content too small to verify magic bytes for declared type.',
      };
    }

    if (isPdf) {
      if (!matchesPrefix(header, PDF_MAGIC)) {
        return {
          valid: false,
          error: 'File declared as PDF does not match PDF magic header (%PDF).',
        };
      }
    } else if (isPng) {
      if (!matchesPrefix(header, PNG_MAGIC)) {
        return {
          valid: false,
          error: 'File declared as PNG does not match PNG magic header.',
        };
      }
    } else if (isJpeg) {
      if (!matchesPrefix(header, JPEG_MAGIC)) {
        return {
          valid: false,
          error: 'File declared as JPEG does not match JPEG magic header.',
        };
      }
    }

    return { valid: true };
  } catch (err: unknown) {
    const message =
      err instanceof Error ? err.message : 'Error inspecting file magic bytes';
    return {
      valid: false,
      error: `Failed to inspect file magic bytes: ${message}`,
    };
  }
}

/**
 * Strips path traversal sequences, control characters, and null bytes from filenames.
 */
export function sanitizeFilename(filename: string): string {
  if (!filename || !filename.trim()) return 'document.bin';
  let clean = filename.replace(/\\/g, '/');
  const lastSlash = clean.lastIndexOf('/');
  if (lastSlash !== -1) {
    clean = clean.substring(lastSlash + 1);
  }

  // Strip null bytes, ASCII control chars [0x00-0x1F, 0x7F-0x9F], and Unicode bidi override/formatting characters:
  // \u200B-\u200F (zero-width & bidi marks), \u202A-\u202E (bidi embedding/override), \u2060-\u206F (invisible formatters), \uFEFF (BOM)
  clean = clean
    .replace(/\0/g, '')
    // eslint-disable-next-line no-control-regex
    .replace(/[\x00-\x1F\x7F-\x9F]/g, '')
    .replace(/[\u200B-\u200F\u202A-\u202E\u2060-\u206F\uFEFF]/g, '')
    .trim();

  // Recursive removal of path traversal sequences '..'
  while (clean.includes('..')) {
    clean = clean.replace(/\.\./g, '');
  }

  // Trim leading/trailing dots and spaces
  clean = clean.replace(/^[.\s]+|[.\s]+$/g, '');

  if (clean.length > MAX_FILENAME_LENGTH) {
    clean = clean.substring(0, MAX_FILENAME_LENGTH);
  }

  return clean || 'document.bin';
}
