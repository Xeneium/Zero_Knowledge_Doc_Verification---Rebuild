package com.zkdoc.api.config;

import com.zkdoc.document.storage.DocumentArtifactStorage;
import com.zkdoc.document.storage.EncryptedDocumentArtifactStorage;
import com.zkdoc.document.storage.FilesystemDocumentArtifactStorage;
import com.zkdoc.document.storage.key.EnvironmentStorageKeyProvider;
import com.zkdoc.document.storage.key.StorageKeyProvider;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Storage configuration providing hardened, persistent filesystem storage with
 * AES-256-GCM envelope encryption for document artifacts (FIND-003).
 */
@Configuration
public class StorageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StorageConfiguration.class);

    @Bean
    public StorageKeyProvider storageKeyProvider(
            @Value("${zkdoc.storage.encryption.key-id:primary-storage-key}") String keyId,
            @Value("${zkdoc.storage.encryption.master-key:}") String masterKey) {
        return new EnvironmentStorageKeyProvider(keyId, masterKey);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "zkdoc.storage.type", havingValue = "filesystem", matchIfMissing = true)
    public DocumentArtifactStorage documentArtifactStorage(
            @Value("${zkdoc.storage.filesystem.base-dir:./data/artifacts}") String baseDir,
            @Value("${zkdoc.storage.encryption.enabled:true}") boolean encryptionEnabled,
            StorageKeyProvider storageKeyProvider) {

        FilesystemDocumentArtifactStorage fsStorage = new FilesystemDocumentArtifactStorage(Paths.get(baseDir));

        if (encryptionEnabled) {
            log.info("Configuring persistent EncryptedDocumentArtifactStorage (AES-256-GCM) with keyId '{}' at: {}",
                    storageKeyProvider.getActiveKeyId(), baseDir);
            return new EncryptedDocumentArtifactStorage(fsStorage, storageKeyProvider, true);
        }

        log.info("Configuring unencrypted FilesystemDocumentArtifactStorage at: {}", baseDir);
        return fsStorage;
    }
}
