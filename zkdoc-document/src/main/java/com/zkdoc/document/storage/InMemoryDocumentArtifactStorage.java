package com.zkdoc.document.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Default resilient artifact storage backed by in-memory buffer for testing and local operation,
 * fully conforming to DocumentArtifactStorage streaming contract.
 */
@Component
public class InMemoryDocumentArtifactStorage implements DocumentArtifactStorage {

    private final ConcurrentHashMap<String, byte[]> storage = new ConcurrentHashMap<>();

    @Override
    public long store(String storageKey, InputStream inputStream, long expectedSize) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        long total = 0;
        while ((read = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
            total += read;
        }
        storage.put(storageKey, buffer.toByteArray());
        return total;
    }

    @Override
    public Optional<InputStream> openStream(String storageKey) throws IOException {
        byte[] bytes = storage.get(storageKey);
        if (bytes == null) {
            return Optional.empty();
        }
        return Optional.of(new ByteArrayInputStream(bytes));
    }

    @Override
    public boolean exists(String storageKey) {
        return storage.containsKey(storageKey);
    }

    @Override
    public boolean delete(String storageKey) throws IOException {
        return storage.remove(storageKey) != null;
    }

    public void clear() {
        storage.clear();
    }
}
