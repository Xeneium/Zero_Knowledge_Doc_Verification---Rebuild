package com.zkdoc.document.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Storage abstraction for persisting, retrieving, and verifying raw document artifact bytes.
 * Decouples domain logic from filesystem, S3, MinIO, or database BLOB mechanisms.
 */
public interface DocumentArtifactStorage {

    /**
     * Streams artifact bytes into storage under the given storage key.
     *
     * @param storageKey unique, opaque server-generated key
     * @param inputStream source content stream
     * @param expectedSize expected size in bytes
     * @return total bytes written
     * @throws IOException on I/O failure
     */
    long store(String storageKey, InputStream inputStream, long expectedSize) throws IOException;

    /**
     * Retrieves an input stream for reading the stored artifact.
     *
     * @param storageKey unique storage key
     * @return InputStream if found
     * @throws IOException on I/O failure
     */
    Optional<InputStream> openStream(String storageKey) throws IOException;

    /**
     * Checks if an artifact exists in storage.
     *
     * @param storageKey unique storage key
     * @return true if artifact exists
     */
    boolean exists(String storageKey);

    /**
     * Deletes an artifact from storage.
     *
     * @param storageKey unique storage key
     * @return true if deleted
     * @throws IOException on I/O failure
     */
    boolean delete(String storageKey) throws IOException;
}
