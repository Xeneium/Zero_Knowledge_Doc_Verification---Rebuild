package com.zkdoc.api.qr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkdoc.api.document.QrCodeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class VerificationCapabilityService {

    public static final Duration DEFAULT_CAPABILITY_TTL = Duration.ofDays(7);
    public static final int TOKEN_ENTROPY_BYTES = 32;

    private final VerificationCapabilityStore capabilityStore;
    private final QrCodeService qrCodeService;
    private final ObjectMapper objectMapper;
    private final String publicVerificationBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public VerificationCapabilityService(
            VerificationCapabilityStore capabilityStore,
            QrCodeService qrCodeService,
            ObjectMapper objectMapper,
            @Value("${zkdoc.public-verification-base-url:http://localhost:8080}") String publicVerificationBaseUrl) {
        this.capabilityStore = Objects.requireNonNull(capabilityStore, "capabilityStore must not be null");
        this.qrCodeService = Objects.requireNonNull(qrCodeService, "qrCodeService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.publicVerificationBaseUrl = publicVerificationBaseUrl.replaceAll("/+$", "");
    }

    /**
     * Mints a new verification capability for a document or credential.
     */
    public VerificationCapability mintCapability(
            UUID credentialId,
            UUID documentId,
            VerificationMode mode,
            String createdBy,
            Duration ttl) {
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");

        Duration effectiveTtl = (ttl != null && !ttl.isNegative() && !ttl.isZero()) ? ttl : DEFAULT_CAPABILITY_TTL;
        Instant now = Instant.now();
        Instant expiresAt = now.plus(effectiveTtl);

        String token = generateSecureToken();
        CapabilityId id = CapabilityId.random();

        VerificationCapability capability = new VerificationCapability(
                id,
                token,
                credentialId,
                documentId,
                mode,
                QrVerificationPayload.PROTOCOL_VERSION_V1,
                CapabilityStatus.ACTIVE,
                createdBy,
                now,
                expiresAt,
                null,
                null
        );

        capabilityStore.save(capability);
        return capability;
    }

    /**
     * Resolves an authoritative verification context by opaque capability token.
     */
    public Optional<CapabilityVerificationContext> resolveContext(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<VerificationCapability> capOpt = capabilityStore.findByToken(token.trim());
        if (capOpt.isEmpty()) {
            return Optional.empty();
        }

        VerificationCapability cap = capOpt.get();
        Instant now = Instant.now();
        boolean usable = cap.isUsable(now);

        String endpoint = publicVerificationBaseUrl + "/api/public/verify/" + cap.capabilityToken();

        return Optional.of(new CapabilityVerificationContext(
                cap.id(),
                cap.capabilityToken(),
                cap.mode(),
                cap.credentialId(),
                cap.documentId(),
                cap.status(),
                usable,
                cap.expiresAt(),
                endpoint
        ));
    }

    /**
     * Authoritatively revokes an existing capability.
     */
    public boolean revokeCapability(CapabilityId id, String reason) {
        Optional<VerificationCapability> capOpt = capabilityStore.findById(id);
        if (capOpt.isEmpty()) {
            return false;
        }
        VerificationCapability cap = capOpt.get();
        if (cap.status() == CapabilityStatus.REVOKED) {
            return true;
        }
        capabilityStore.updateStatus(id, CapabilityStatus.REVOKED, Instant.now(), reason != null ? reason : "Revoked by issuer/owner");
        return true;
    }

    /**
     * Generates a PNG QR code containing the canonical verification payload.
     * Strictest privacy invariant: only canonical metadata & capability token URI are encoded.
     */
    public byte[] generateQrCodePng(VerificationCapability capability) {
        String uri = publicVerificationBaseUrl + "/api/public/verify/" + capability.capabilityToken();
        QrVerificationPayload payload = new QrVerificationPayload(
                capability.protocolVersion(),
                capability.mode(),
                capability.capabilityToken(),
                uri
        );
        String canonicalJson = payload.toCanonicalJson(objectMapper);
        return qrCodeService.pngFor(canonicalJson);
    }

    /**
     * Generates a PNG QR code encoding the direct canonical public verification URL.
     */
    public byte[] generateDirectUrlQrPng(String capabilityToken) {
        String uri = publicVerificationBaseUrl + "/api/public/verify/" + capabilityToken;
        return qrCodeService.pngFor(uri);
    }

    /**
     * Parses a scanned QR payload (either canonical JSON or direct verification URL).
     */
    public String extractCapabilityToken(String rawQrContent) {
        if (rawQrContent == null || rawQrContent.isBlank()) {
            throw new IllegalArgumentException("Scanned QR content cannot be empty");
        }
        String trimmed = rawQrContent.trim();
        // Check if raw content is a canonical JSON payload
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            QrVerificationPayload payload = QrVerificationPayload.fromJson(trimmed, objectMapper);
            return payload.capabilityToken();
        }
        // Check if raw content is a direct verification URI
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            int verifyIdx = trimmed.indexOf("/api/public/verify/");
            if (verifyIdx != -1) {
                String token = trimmed.substring(verifyIdx + "/api/public/verify/".length());
                int queryIdx = token.indexOf('?');
                if (queryIdx != -1) token = token.substring(0, queryIdx);
                int hashIdx = token.indexOf('#');
                if (hashIdx != -1) token = token.substring(0, hashIdx);
                if (!token.isBlank()) {
                    return token;
                }
            }
        }
        // Otherwise treat as raw opaque token if format matches
        if (trimmed.length() >= 32 && trimmed.length() <= 64 && trimmed.matches("^[A-Za-z0-9_-]+$")) {
            return trimmed;
        }
        throw new IllegalArgumentException("Unsupported or unrecognized QR verification format");
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_ENTROPY_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
