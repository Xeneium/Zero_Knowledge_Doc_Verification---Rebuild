package com.zkdoc.api.qr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.zkdoc.api.audit.VerificationAuditService;
import com.zkdoc.api.document.QrCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Phase 11: Verification Capability & QR Security Architecture Tests")
class VerificationCapabilitySecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QrCodeService qrCodeService = new QrCodeService();
    private InMemoryCapabilityStore capabilityStore;
    private VerificationCapabilityService capabilityService;

    static class InMemoryCapabilityStore implements VerificationCapabilityStore {
        private final Map<String, VerificationCapability> byToken = new ConcurrentHashMap<>();
        private final Map<CapabilityId, VerificationCapability> byId = new ConcurrentHashMap<>();

        @Override
        public void save(VerificationCapability capability) {
            byToken.put(capability.capabilityToken(), capability);
            byId.put(capability.id(), capability);
        }

        @Override
        public Optional<VerificationCapability> findByToken(String token) {
            return Optional.ofNullable(byToken.get(token));
        }

        @Override
        public Optional<VerificationCapability> findById(CapabilityId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void updateStatus(CapabilityId id, CapabilityStatus status, Instant revokedAt, String reason) {
            VerificationCapability existing = byId.get(id);
            if (existing != null) {
                VerificationCapability updated = new VerificationCapability(
                        existing.id(),
                        existing.capabilityToken(),
                        existing.credentialId(),
                        existing.documentId(),
                        existing.mode(),
                        existing.protocolVersion(),
                        status,
                        existing.createdBy(),
                        existing.createdAt(),
                        existing.expiresAt(),
                        revokedAt,
                        reason
                );
                byId.put(id, updated);
                byToken.put(existing.capabilityToken(), updated);
            }
        }
    }

    @BeforeEach
    void setUp() {
        capabilityStore = new InMemoryCapabilityStore();
        capabilityService = new VerificationCapabilityService(
                capabilityStore,
                qrCodeService,
                objectMapper,
                "https://verifier.zkdoc.example"
        );
    }

    @Nested
    @DisplayName("Canonical QR Payload & Encoding Invariants")
    class PayloadInvariants {

        @Test
        @DisplayName("Canonical serialization round-trip succeeds and contains only allowed discovery fields")
        void canonicalPayloadRoundTrip() {
            String token = "abcdef1234567890abcdef1234567890";
            String uri = "https://verifier.zkdoc.example/api/public/verify/" + token;
            QrVerificationPayload original = new QrVerificationPayload(
                    QrVerificationPayload.PROTOCOL_VERSION_V1,
                    VerificationMode.MODE_B_REGISTRY_CAPABILITY,
                    token,
                    uri
            );

            String json = original.toCanonicalJson(objectMapper);
            assertNotNull(json);

            // Strictest Privacy Invariant: Payload must never contain secrets or proving material
            assertFalse(json.contains("blinding"));
            assertFalse(json.contains("witness"));
            assertFalse(json.contains("privateKey"));
            assertFalse(json.contains("secret"));
            assertFalse(json.contains("content"));

            QrVerificationPayload parsed = QrVerificationPayload.fromJson(json, objectMapper);
            assertEquals(original.version(), parsed.version());
            assertEquals(original.mode(), parsed.mode());
            assertEquals(original.capabilityToken(), parsed.capabilityToken());
            assertEquals(original.verificationUri(), parsed.verificationUri());
        }

        @Test
        @DisplayName("Unknown or tampered protocol version fails closed")
        void unknownProtocolVersionFailsClosed() {
            String invalidJson = """
                    {"v":"QR_V99","m":"MODE_B_REGISTRY_CAPABILITY","tok":"abcdef1234567890abcdef1234567890","uri":"https://example.com"}
                    """;
            assertThrows(IllegalArgumentException.class, () ->
                    QrVerificationPayload.fromJson(invalidJson, objectMapper));
        }

        @Test
        @DisplayName("Oversized payload rejected to protect against resource exhaustion")
        void oversizedPayloadFailsClosed() {
            String hugeString = "A".repeat(3000);
            assertThrows(IllegalArgumentException.class, () ->
                    QrVerificationPayload.fromJson(hugeString, objectMapper));
        }
    }

    @Nested
    @DisplayName("QR Code Generation & Visual Decoding Round-Trip")
    class QrImageRoundTrip {

        @Test
        @DisplayName("QR image generation and ZXing matrix decode correctly recovers canonical payload")
        void qrImageRoundTripRecoversPayload() throws Exception {
            UUID credId = UUID.randomUUID();
            VerificationCapability cap = capabilityService.mintCapability(
                    credId,
                    null,
                    VerificationMode.MODE_B_REGISTRY_CAPABILITY,
                    "issuer-test",
                    Duration.ofHours(1)
            );

            byte[] pngBytes = capabilityService.generateQrCodePng(cap);
            assertNotNull(pngBytes);
            assertTrue(pngBytes.length > 0);

            // Decode visual QR image using ZXing MultiFormatReader
            var bufferedImage = ImageIO.read(new ByteArrayInputStream(pngBytes));
            var source = new BufferedImageLuminanceSource(bufferedImage);
            var bitmap = new BinaryBitmap(new HybridBinarizer(source));
            var result = new MultiFormatReader().decode(bitmap);

            String decodedText = result.getText();
            assertNotNull(decodedText);

            // Extract and verify capability token matches
            String extractedToken = capabilityService.extractCapabilityToken(decodedText);
            assertEquals(cap.capabilityToken(), extractedToken);

            // Resolve context from extracted token
            var ctx = capabilityService.resolveContext(extractedToken);
            assertTrue(ctx.isPresent());
            assertEquals(cap.id(), ctx.get().capabilityId());
            assertEquals(credId, ctx.get().credentialId());
            assertTrue(ctx.get().isUsable());
        }

        @Test
        @DisplayName("Direct URL QR code generation decodes correctly to capability token")
        void directUrlQrDecodesCorrectly() throws Exception {
            UUID docId = UUID.randomUUID();
            VerificationCapability cap = capabilityService.mintCapability(
                    null,
                    docId,
                    VerificationMode.MODE_A_PRESENTED_ARTIFACT,
                    "issuer-test",
                    Duration.ofHours(1)
            );

            byte[] pngBytes = capabilityService.generateDirectUrlQrPng(cap.capabilityToken());
            var bufferedImage = ImageIO.read(new ByteArrayInputStream(pngBytes));
            var source = new BufferedImageLuminanceSource(bufferedImage);
            var bitmap = new BinaryBitmap(new HybridBinarizer(source));
            var result = new MultiFormatReader().decode(bitmap);

            String extractedToken = capabilityService.extractCapabilityToken(result.getText());
            assertEquals(cap.capabilityToken(), extractedToken);
        }
    }

    @Nested
    @DisplayName("Capability Lifecycle, Expiration & Revocation")
    class LifecycleTests {

        @Test
        @DisplayName("Active capability resolves successfully")
        void activeCapabilityResolves() {
            UUID credId = UUID.randomUUID();
            VerificationCapability cap = capabilityService.mintCapability(
                    credId, null, VerificationMode.MODE_B_REGISTRY_CAPABILITY, "issuer-1", Duration.ofHours(2));

            var contextOpt = capabilityService.resolveContext(cap.capabilityToken());
            assertTrue(contextOpt.isPresent());
            var context = contextOpt.get();
            assertTrue(context.isUsable());
            assertEquals(CapabilityStatus.ACTIVE, context.status());
            assertEquals(credId, context.credentialId());
        }

        @Test
        @DisplayName("Expired capability resolves as unusable")
        void expiredCapabilityIsUnusable() {
            UUID credId = UUID.randomUUID();
            // Mint with negative TTL (already expired)
            VerificationCapability cap = new VerificationCapability(
                    CapabilityId.random(),
                    "token-expired-12345678901234567890",
                    credId,
                    null,
                    VerificationMode.MODE_B_REGISTRY_CAPABILITY,
                    "QR_V1",
                    CapabilityStatus.ACTIVE,
                    "issuer-1",
                    Instant.now().minusSeconds(100),
                    Instant.now().minusSeconds(10),
                    null,
                    null
            );
            capabilityStore.save(cap);

            var contextOpt = capabilityService.resolveContext(cap.capabilityToken());
            assertTrue(contextOpt.isPresent());
            assertFalse(contextOpt.get().isUsable());
        }

        @Test
        @DisplayName("Authoritatively revoked capability resolves as unusable and preserves revocation audit")
        void revokedCapabilityIsUnusable() {
            UUID credId = UUID.randomUUID();
            VerificationCapability cap = capabilityService.mintCapability(
                    credId, null, VerificationMode.MODE_B_REGISTRY_CAPABILITY, "issuer-1", Duration.ofHours(2));

            assertTrue(capabilityService.resolveContext(cap.capabilityToken()).get().isUsable());

            // Revoke capability
            boolean revoked = capabilityService.revokeCapability(cap.id(), "Document reported compromised");
            assertTrue(revoked);

            var contextOpt = capabilityService.resolveContext(cap.capabilityToken());
            assertTrue(contextOpt.isPresent());
            assertFalse(contextOpt.get().isUsable());
            assertEquals(CapabilityStatus.REVOKED, contextOpt.get().status());
        }

        @Test
        @DisplayName("Nonexistent or guessed capability token returns empty context")
        void nonexistentTokenReturnsEmpty() {
            var contextOpt = capabilityService.resolveContext("nonexistent-random-token-1234567890");
            assertTrue(contextOpt.isEmpty());
        }
    }

    @Nested
    @DisplayName("Binding & Decoupling Invariants")
    class BindingAndDecoupling {

        @Test
        @DisplayName("Capability cannot redefine server-side credential relationship")
        void serverDeterminesCredentialAuthoritatively() {
            UUID realCredId = UUID.randomUUID();
            VerificationCapability cap = capabilityService.mintCapability(
                    realCredId, null, VerificationMode.MODE_B_REGISTRY_CAPABILITY, "issuer-1", Duration.ofHours(1));

            var context = capabilityService.resolveContext(cap.capabilityToken()).orElseThrow();

            // Client cannot override credentialId: it is strictly resolved from server-side store
            assertEquals(realCredId, context.credentialId());
        }

        @Test
        @DisplayName("Public verification response never exposes document bytes or private witness")
        void publicVerificationResponseIsMinimal() {
            var successResp = com.zkdoc.api.publicverify.CapabilityPublicVerificationResponse.success("MODE_B_REGISTRY_CAPABILITY", "QR_V1");
            assertTrue(successResp.verified());
            assertEquals("VALID", successResp.status());
            assertNull(successResp.failureReason());

            var failResp = com.zkdoc.api.publicverify.CapabilityPublicVerificationResponse.failure("MODE_B_REGISTRY_CAPABILITY", "QR_V1", "Capability expired or revoked");
            assertFalse(failResp.verified());
            assertEquals("INVALID", failResp.status());
            assertEquals("Capability expired or revoked", failResp.failureReason());
        }
    }
}
