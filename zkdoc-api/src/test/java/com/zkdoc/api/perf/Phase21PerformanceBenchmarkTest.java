package com.zkdoc.api.perf;

import com.weavechain.ec.Scalar;
import com.weavechain.zk.bulletproofs.BulletProofs;
import com.weavechain.zk.bulletproofs.PedersenCommitment;
import com.weavechain.zk.bulletproofs.Utils;
import com.zkdoc.api.audit.ActorType;
import com.zkdoc.api.audit.AuditEventRequest;
import com.zkdoc.api.audit.AuditEventType;
import com.zkdoc.api.audit.AuditIntegrityResult;
import com.zkdoc.api.audit.AuditOutcome;
import com.zkdoc.api.audit.AuditQueryFilter;
import com.zkdoc.api.audit.JdbcSecurityAuditService;
import com.zkdoc.api.audit.TargetType;
import com.zkdoc.api.ratelimit.BucketRateLimitService;
import com.zkdoc.api.ratelimit.RateLimitCategory;
import com.zkdoc.api.ratelimit.RateLimitDecision;
import com.zkdoc.api.ratelimit.RateLimitKey;
import com.zkdoc.core.crypto.RsaPssSignatureService;
import com.zkdoc.document.Sha256Hasher;
import com.zkdoc.keys.KeyOwnerType;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import com.zkdoc.zkp.engine.PredicateProofArtifact;
import com.zkdoc.zkp.engine.WeavechainBulletproofPredicateEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 21: Performance, Concurrency & Scalability Benchmark Suite.
 * <p>
 * Measures baseline latency percentiles (p50, p95, p99, mean) and throughput (ops/sec)
 * across core cryptographic, database, audit chaining, and concurrent operations.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase21PerformanceBenchmarkTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static KeyPair rsaKeyPair;
    private static RegisteredKey registeredKey;
    private static Sha256Hasher sha256Hasher;
    private static RsaPssSignatureService signatureService;
    private static WeavechainBulletproofPredicateEngine zkpEngine;
    private static PedersenCommitment pedersenCommitment;

    public record BenchmarkResult(
            String metricName,
            int sampleCount,
            double meanMs,
            double p50Ms,
            double p95Ms,
            double p99Ms,
            double opsPerSec) {

        public void print() {
            System.out.printf("[PHASE-21 BENCHMARK] %-38s | %10.2f ops/s | mean: %7.3f ms | p50: %7.3f ms | p95: %7.3f ms | p99: %7.3f ms (%d samples)%n",
                    metricName, opsPerSec, meanMs, p50Ms, p95Ms, p99Ms, sampleCount);
        }
    }

    public static BenchmarkResult measure(String metricName, int warmup, int samples, Runnable runnable) {
        for (int i = 0; i < warmup; i++) {
            runnable.run();
        }
        double[] durations = new double[samples];
        long overallStart = System.nanoTime();
        for (int i = 0; i < samples; i++) {
            long start = System.nanoTime();
            runnable.run();
            durations[i] = (System.nanoTime() - start) / 1_000_000.0;
        }
        long overallElapsed = System.nanoTime() - overallStart;
        Arrays.sort(durations);
        double totalMs = overallElapsed / 1_000_000.0;
        double meanMs = totalMs / samples;
        double p50Ms = durations[(int) (samples * 0.50)];
        double p95Ms = durations[(int) (samples * 0.95)];
        double p99Ms = durations[(int) (samples * 0.99)];
        double opsPerSec = samples / (overallElapsed / 1_000_000_000.0);

        BenchmarkResult result = new BenchmarkResult(metricName, samples, meanMs, p50Ms, p95Ms, p99Ms, opsPerSec);
        result.print();
        return result;
    }

    @BeforeAll
    static void initCrypto() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, RANDOM);
        rsaKeyPair = kpg.generateKeyPair();

        String pem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(rsaKeyPair.getPublic().getEncoded()) +
                "\n-----END PUBLIC KEY-----";

        registeredKey = new RegisteredKey(
                UUID.randomUUID(),
                "test-issuer-key-21",
                KeyPurpose.DOCUMENT_SIGNING,
                KeyStatus.ACTIVE,
                "RSASSA-PSS",
                "SHA-256/MGF1-SHA256/salt=32",
                "issuer-1",
                KeyOwnerType.ISSUER,
                pem,
                rsaKeyPair.getPublic().getEncoded(),
                "X.509/PEM",
                "fp-test-21",
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                null,
                1,
                Collections.emptyMap());

        sha256Hasher = new Sha256Hasher();
        signatureService = new RsaPssSignatureService();
        zkpEngine = new WeavechainBulletproofPredicateEngine();
        pedersenCommitment = PedersenCommitment.getDefault();
    }

    @Test
    @Order(1)
    @DisplayName("Benchmark SHA-256 Hashing across payload sizes (1KB, 100KB, 5MB)")
    void benchmarkSha256Hashing() {
        byte[] payload1Kb = new byte[1024];
        byte[] payload100Kb = new byte[100 * 1024];
        byte[] payload5Mb = new byte[5 * 1024 * 1024];
        RANDOM.nextBytes(payload1Kb);
        RANDOM.nextBytes(payload100Kb);
        RANDOM.nextBytes(payload5Mb);

        measure("SHA-256 (1 KB Payload)", 100, 1000, () -> {
            byte[] digest = sha256Hasher.hash(payload1Kb);
            assertThat(digest).hasSize(32);
        });

        measure("SHA-256 (100 KB Payload)", 50, 500, () -> {
            byte[] digest = sha256Hasher.hash(payload100Kb);
            assertThat(digest).hasSize(32);
        });

        measure("SHA-256 (5 MB Payload)", 10, 50, () -> {
            byte[] digest = sha256Hasher.hash(payload5Mb);
            assertThat(digest).hasSize(32);
        });
    }

    @Test
    @Order(2)
    @DisplayName("Benchmark RSA-PSS 2048-bit Signing and Signature Verification")
    void benchmarkRsaPssSigningAndVerification() {
        byte[] testDigest = new byte[32];
        RANDOM.nextBytes(testDigest);

        byte[] sampleSignature = signatureService.signRsaPss(testDigest, rsaKeyPair.getPrivate());
        assertThat(sampleSignature).isNotEmpty();

        measure("RSA-PSS 2048-bit Sign (32B Digest)", 20, 200, () -> {
            byte[] sig = signatureService.signRsaPss(testDigest, rsaKeyPair.getPrivate());
            assertThat(sig).isNotEmpty();
        });

        measure("RSA-PSS 2048-bit Verify", 20, 200, () -> {
            boolean valid = signatureService.verifyRsaPss(testDigest, sampleSignature, rsaKeyPair.getPublic());
            assertTrue(valid);
        });
    }

    @Test
    @Order(3)
    @DisplayName("Benchmark RegisteredKey.toPublicKey() Public Key Resolution")
    void benchmarkRegisteredKeyToPublicKey() {
        measure("RegisteredKey.toPublicKey Resolution", 50, 1000, () -> {
            java.security.PublicKey pk = registeredKey.toPublicKey();
            assertThat(pk).isNotNull();
        });
    }

    @Test
    @Order(4)
    @DisplayName("Benchmark Pedersen Commitment Generation (C = vG + rH)")
    void benchmarkPedersenCommitment() {
        measure("Pedersen Commitment (Curve25519)", 20, 200, () -> {
            byte[] blinding = new byte[32];
            RANDOM.nextBytes(blinding);
            Scalar value = Utils.scalar(25L);
            Scalar blind = BulletProofs.getFactory().fromBits(blinding);
            byte[] point = pedersenCommitment.commit(value, blind).toByteArray();
            assertThat(point).hasSize(32);
        });
    }

    @Test
    @Order(5)
    @DisplayName("Benchmark Bulletproof Predicate Proof Generation and Verification")
    void benchmarkBulletproofPredicateEngine() {
        long value = 25L;
        long threshold = 18L;
        byte[] blinding = Utils.scalar(987654321L).toByteArray();
        byte[] nonce = "perf-nonce-test-12345".getBytes();

        Scalar valScalar = Utils.scalar(value);
        Scalar blindScalar = BulletProofs.getFactory().fromBits(blinding);
        byte[] commitmentPoint = pedersenCommitment.commit(valScalar, blindScalar).toByteArray();

        PredicateProofArtifact artifact = zkpEngine.proveGreaterThanOrEqual(value, blinding, threshold, nonce);
        assertThat(artifact.proofBytes()).isNotEmpty();

        measure("Bulletproof Proof Proving (>= 18)", 2, 10, () -> {
            PredicateProofArtifact art = zkpEngine.proveGreaterThanOrEqual(value, blinding, threshold, nonce);
            assertThat(art.proofBytes()).isNotEmpty();
        });

        measure("Bulletproof Proof Verification", 2, 10, () -> {
            boolean verified = zkpEngine.verifyGreaterThanOrEqual(commitmentPoint, threshold, nonce, artifact);
            assertTrue(verified);
        });
    }

    @Test
    @Order(6)
    @DisplayName("Benchmark Audit Logging Record and Integrity Verification Throughput")
    void benchmarkAuditLoggingAndIntegrity() throws Exception {
        Path tempDb = Files.createTempFile("zkdoc-perf-audit-", ".db");
        tempDb.toFile().deleteOnExit();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + tempDb.toAbsolutePath());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.execute("""
                CREATE TABLE security_audit_events (
                    id TEXT PRIMARY KEY,
                    sequence_number INTEGER NOT NULL UNIQUE,
                    event_type TEXT NOT NULL,
                    event_category TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    actor_subject TEXT NOT NULL,
                    actor_type TEXT NOT NULL,
                    target_type TEXT,
                    target_id TEXT,
                    action TEXT NOT NULL,
                    outcome TEXT NOT NULL,
                    reason_code TEXT,
                    correlation_id TEXT NOT NULL,
                    client_ip_hash TEXT NOT NULL,
                    metadata_json TEXT,
                    previous_event_hash TEXT NOT NULL,
                    event_hash TEXT NOT NULL
                );
                CREATE INDEX idx_sec_audit_seq ON security_audit_events(sequence_number);
                """);

        JdbcSecurityAuditService auditService = new JdbcSecurityAuditService(jdbcTemplate, Clock.systemUTC());

        measure("Audit Record Append (Sequential)", 10, 100, () -> {
            auditService.record(AuditEventRequest.builder(AuditEventType.CREDENTIAL_ISSUED)
                    .actor("perf-admin", ActorType.ADMIN)
                    .target(TargetType.CREDENTIAL, UUID.randomUUID())
                    .action("ISSUE")
                    .outcome(AuditOutcome.SUCCESS)
                    .build());
        });

        long eventCount = auditService.countEvents(new AuditQueryFilter(null, null, null, null, null, null, null, null, 0, 1000));
        assertThat(eventCount).isGreaterThanOrEqualTo(100);

        measure("Audit Trail Integrity Verification (100 events)", 5, 20, () -> {
            AuditIntegrityResult result = auditService.verifyChainIntegrity();
            assertTrue(result.valid());
        });
    }

    @Test
    @Order(7)
    @DisplayName("Concurrent Stress Test: 10 Threads Verifying Bulletproofs")
    void testConcurrentBulletproofVerification() throws Exception {
        long value = 30L;
        long threshold = 21L;
        byte[] blinding = Utils.scalar(555444333L).toByteArray();
        byte[] nonce = "concurrent-nonce-xyz".getBytes();

        Scalar valScalar = Utils.scalar(value);
        Scalar blindScalar = BulletProofs.getFactory().fromBits(blinding);
        byte[] commitmentPoint = pedersenCommitment.commit(valScalar, blindScalar).toByteArray();
        PredicateProofArtifact artifact = zkpEngine.proveGreaterThanOrEqual(value, blinding, threshold, nonce);

        int threads = 10;
        int operationsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        long startNs = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        boolean valid = zkpEngine.verifyGreaterThanOrEqual(commitmentPoint, threshold, nonce, artifact);
                        if (valid) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(endGate.await(30, TimeUnit.SECONDS), "Concurrent verification should complete within 30s");
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(threads * operationsPerThread);
        System.out.printf("[CONCURRENCY] Bulletproof Concurrent Verification: %d ops across %d threads in %d ms (%.2f ops/sec)%n",
                successCount.get(), threads, elapsedMs, (successCount.get() / (elapsedMs / 1000.0)));
    }

    @Test
    @Order(8)
    @DisplayName("Concurrent Stress Test: 10 Threads Appending to Security Audit Log")
    void testConcurrentAuditLoggingIntegrity() throws Exception {
        Path tempDb = Files.createTempFile("zkdoc-concurrent-audit-", ".db");
        tempDb.toFile().deleteOnExit();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + tempDb.toAbsolutePath());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.execute("""
                CREATE TABLE security_audit_events (
                    id TEXT PRIMARY KEY,
                    sequence_number INTEGER NOT NULL UNIQUE,
                    event_type TEXT NOT NULL,
                    event_category TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    actor_subject TEXT NOT NULL,
                    actor_type TEXT NOT NULL,
                    target_type TEXT,
                    target_id TEXT,
                    action TEXT NOT NULL,
                    outcome TEXT NOT NULL,
                    reason_code TEXT,
                    correlation_id TEXT NOT NULL,
                    client_ip_hash TEXT NOT NULL,
                    metadata_json TEXT,
                    previous_event_hash TEXT NOT NULL,
                    event_hash TEXT NOT NULL
                );
                CREATE INDEX idx_sec_audit_seq ON security_audit_events(sequence_number);
                """);

        JdbcSecurityAuditService auditService = new JdbcSecurityAuditService(jdbcTemplate, Clock.systemUTC());

        int threads = 10;
        int eventsPerThread = 15;
        int totalExpectedEvents = threads * eventsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        long startNs = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < eventsPerThread; i++) {
                        auditService.record(AuditEventRequest.builder(AuditEventType.AUTHENTICATION_SUCCESS)
                                .actor("user-" + threadId, ActorType.USER)
                                .target(TargetType.SYSTEM, "target-" + i)
                                .action("LOGIN")
                                .outcome(AuditOutcome.SUCCESS)
                                .build());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(endGate.await(30, TimeUnit.SECONDS), "Concurrent audit writes should complete within 30s");
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        executor.shutdown();

        long count = auditService.countEvents(new AuditQueryFilter(null, null, null, null, null, null, null, null, 0, 1000));
        assertThat(count).isEqualTo(totalExpectedEvents);

        // Verify cryptographic hash chain across all 150 events
        AuditIntegrityResult integrity = auditService.verifyChainIntegrity();
        assertTrue(integrity.valid(), "Hash chain must be unbroken after concurrent multi-threaded appends: " + integrity.failureReason());
        assertThat(integrity.checkedCount()).isEqualTo(totalExpectedEvents);

        System.out.printf("[CONCURRENCY] Concurrent Audit Append: %d events across %d threads in %d ms (%.2f ops/sec) — Hash chain 100%% verified%n",
                totalExpectedEvents, threads, elapsedMs, (totalExpectedEvents / (elapsedMs / 1000.0)));
    }

    @Test
    @Order(9)
    @DisplayName("Concurrent Stress Test: Rate Limiter Token Bucket Acquisition under Contention")
    void testConcurrentRateLimiting() throws Exception {
        BucketRateLimitService rateLimiter = new BucketRateLimitService();
        RateLimitKey key = RateLimitKey.of(RateLimitCategory.PUBLIC_VERIFICATION, "test-ip-1.2.3.4");

        int threads = 10;
        int callsPerThread = 50;
        int totalRequests = threads * callsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        long startNs = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < callsPerThread; i++) {
                        RateLimitDecision decision = rateLimiter.tryAcquire(key);
                        if (decision.allowed()) {
                            allowed.incrementAndGet();
                        } else {
                            rejected.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(endGate.await(10, TimeUnit.SECONDS));
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        executor.shutdown();

        int capacity = RateLimitCategory.PUBLIC_VERIFICATION.getCapacity();
        assertThat(allowed.get()).isLessThanOrEqualTo(capacity);
        assertThat(allowed.get() + rejected.get()).isEqualTo(totalRequests);

        System.out.printf("[CONCURRENCY] Rate Limiter Under Contention: %d total requests across %d threads in %d ms (%d allowed, %d rejected)%n",
                totalRequests, threads, elapsedMs, allowed.get(), rejected.get());
    }
}
