package com.zkdoc.api.config;

import com.zkdoc.document.storage.DocumentArtifactStorage;
import com.zkdoc.document.storage.FilesystemDocumentArtifactStorage;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Storage configuration providing hardened, persistent filesystem storage for document artifacts
 * in production and containerized environments.
 */
@Configuration
public class StorageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StorageConfiguration.class);

    @Bean
    @Primary
    @ConditionalOnProperty(name = "zkdoc.storage.type", havingValue = "filesystem", matchIfMissing = true)
    public DocumentArtifactStorage filesystemDocumentArtifactStorage(
            @Value("${zkdoc.storage.filesystem.base-dir:./data/artifacts}") String baseDir) {
        log.info("Configuring persistent FilesystemDocumentArtifactStorage at: {}", baseDir);
        return new FilesystemDocumentArtifactStorage(Paths.get(baseDir));
    }
}
