package com.zkdoc.document.storage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Validates uploaded document content by inspecting magic numbers and content-type headers.
 * Protects against MIME spoofing, polyglots, and executable script uploads.
 */
public final class DocumentContentValidator {

    public static final long MAX_ALLOWED_FILE_SIZE = 25 * 1024 * 1024; // 25 MB max bound
    public static final int MAX_FILENAME_LENGTH = 255;

    // Supported document types
    public static final String TYPE_PDF = "application/pdf";
    public static final String TYPE_PNG = "image/png";
    public static final String TYPE_JPEG = "image/jpeg";
    public static final String TYPE_TEXT = "text/plain";

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            TYPE_PDF, TYPE_PNG, TYPE_JPEG, TYPE_TEXT);

    // Magic byte signatures
    private static final byte[] PDF_MAGIC = new byte[] {0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final byte[] PNG_MAGIC = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private DocumentContentValidator() {}

    /**
     * Sanitizes untrusted user filenames, stripping directory traversals, null bytes, and control characters.
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document.bin";
        }
        // Normalize slashes
        String clean = filename.replace('\\', '/');
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash != -1) {
            clean = clean.substring(lastSlash + 1);
        }
        // Strip control chars, null bytes, path traversal sequences
        clean = clean.replace("\0", "").replaceAll("[\\r\\n\\t]", "").trim();
        clean = clean.replace("..", "");
        if (clean.length() > MAX_FILENAME_LENGTH) {
            clean = clean.substring(0, MAX_FILENAME_LENGTH);
        }
        return clean.isBlank() ? "document.bin" : clean;
    }

    /**
     * Inspects magic bytes from an input stream to verify declared Content-Type.
     * The input stream must support mark and reset.
     */
    public static void validateContent(InputStream inputStream, String declaredContentType) throws IOException {
        if (!inputStream.markSupported()) {
            throw new IllegalArgumentException("InputStream must support mark/reset for content inspection");
        }

        String normalizedContentType = declaredContentType != null ? declaredContentType.toLowerCase(Locale.ROOT).trim() : "";
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new IllegalArgumentException("Unsupported or forbidden document Content-Type: " + declaredContentType);
        }

        inputStream.mark(16);
        byte[] header = new byte[16];
        int read = inputStream.read(header);
        inputStream.reset();

        if (TYPE_TEXT.equals(normalizedContentType)) {
            // For text/plain, ensure no null bytes or binary executable headers (like ELF or MZ)
            if (read >= 4 && ((header[0] == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') ||
                (header[0] == 'M' && header[1] == 'Z'))) {
                throw new IllegalArgumentException("Binary or executable content rejected for text/plain document");
            }
            if (containsNullBytes(header, read)) {
                throw new IllegalArgumentException("Binary or executable content rejected for text/plain document");
            }
            return;
        }

        if (read < 3) {
            throw new IllegalArgumentException("File content too small to verify magic bytes for declared type: " + declaredContentType);
        }

        if (TYPE_PDF.equals(normalizedContentType)) {
            if (!matchesPrefix(header, PDF_MAGIC)) {
                throw new IllegalArgumentException("File declared as PDF does not match PDF magic header (%PDF)");
            }
        } else if (TYPE_PNG.equals(normalizedContentType)) {
            if (!matchesPrefix(header, PNG_MAGIC)) {
                throw new IllegalArgumentException("File declared as PNG does not match PNG magic header");
            }
        } else if (TYPE_JPEG.equals(normalizedContentType)) {
            if (!matchesPrefix(header, JPEG_MAGIC)) {
                throw new IllegalArgumentException("File declared as JPEG does not match JPEG magic header");
            }
        }
    }

    private static boolean matchesPrefix(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private static boolean containsNullBytes(byte[] data, int length) {
        for (int i = 0; i < length; i++) {
            if (data[i] == 0) return true;
        }
        return false;
    }
}
