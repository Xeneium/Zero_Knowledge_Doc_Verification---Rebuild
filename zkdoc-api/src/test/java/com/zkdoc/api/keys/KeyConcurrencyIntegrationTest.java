package com.zkdoc.api.keys;

import com.zkdoc.api.Application;
import com.zkdoc.keys.KeyOwnerType;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistry;
import com.zkdoc.keys.KeyRegistryService;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = Application.class)
class KeyConcurrencyIntegrationTest {

    private static final Path DB_PATH = createTempDb();

    @DynamicPropertySource
    static void configureSqlite(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath());
    }

    private static Path createTempDb() {
        try {
            return Files.createTempDirectory("zkdoc-concurrency-it-").resolve("concurrency-it.db");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create temp db", e);
        }
    }

    private static KeyPair testKeyPair;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        testKeyPair = gen.generateKeyPair();
    }

    @Autowired private KeyRegistry keyRegistry;
    @Autowired private KeyRegistryService keyRegistryService;

    @Test
    @DisplayName("Concurrent reads and verification queries execute safely without corruption")
    void concurrentKeyLookupsAndVerifications() throws Exception {
        String keyId = "concurrent-key-" + UUID.randomUUID();
        RegisteredKey key = RegisteredKey.createActive(
                keyId, KeyPurpose.CREDENTIAL_SIGNING, "concurrent-owner", KeyOwnerType.ISSUER, testKeyPair.getPublic());
        keyRegistry.register(key);

        int threadCount = 10;
        int operationsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successfulReads = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        if (keyRegistry.findForVerification(keyId, KeyPurpose.CREDENTIAL_SIGNING).isPresent()) {
                            successfulReads.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All concurrent tasks should complete within timeout");
        assertEquals(threadCount * operationsPerThread, successfulReads.get());
    }
}
