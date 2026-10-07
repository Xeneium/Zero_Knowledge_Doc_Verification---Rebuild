package com.zkdoc.api.qr;

import com.zkdoc.api.audit.VerificationAuditService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Controller for issuing, inspecting, and revoking Verification Capabilities and QR codes.
 * Accessible only to authenticated roles (ISSUER, HOLDER, ADMIN).
 * Enforces strict object-level authorization and privacy boundaries.
 */
@RestController
@RequestMapping("/api/capabilities")
public class VerificationCapabilityController {

    private final VerificationCapabilityService capabilityService;
    private final VerificationAuditService verificationAuditService;

    public VerificationCapabilityController(
            VerificationCapabilityService capabilityService,
            VerificationAuditService verificationAuditService) {
        this.capabilityService = Objects.requireNonNull(capabilityService, "capabilityService must not be null");
        this.verificationAuditService = Objects.requireNonNull(verificationAuditService, "verificationAuditService must not be null");
    }

    /**
     * Mints a new verification capability for a credential or document.
     */
    @PostMapping
    public ResponseEntity<CapabilityResponse> createCapability(
            @RequestBody CreateCapabilityRequest request,
            Authentication authentication) {
        if (request.mode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VerificationMode must be specified");
        }
        if (request.credentialId() == null && request.documentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either credentialId or documentId must be provided");
        }

        String caller = (authentication != null && authentication.getName() != null) ? authentication.getName() : "anonymous";
        Duration ttl = (request.ttlSeconds() != null && request.ttlSeconds() > 0)
                ? Duration.ofSeconds(request.ttlSeconds())
                : VerificationCapabilityService.DEFAULT_CAPABILITY_TTL;

        VerificationCapability capability = capabilityService.mintCapability(
                request.credentialId(),
                request.documentId(),
                request.mode(),
                caller,
                ttl
        );

        verificationAuditService.record(authentication, "QR_CAPABILITY_CREATED",
                capability.credentialId(), capability.documentId(), true);

        return ResponseEntity.status(HttpStatus.CREATED).body(CapabilityResponse.from(capability));
    }

    /**
     * Downloads the QR code image representing the capability.
     * Cache-Control is set to no-store to prevent caching of bearer capability visual tokens.
     */
    @GetMapping(value = "/{token}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCode(
            @PathVariable String token,
            @RequestParam(name = "format", defaultValue = "canonical") String format,
            Authentication authentication) {
        var contextOpt = capabilityService.resolveContext(token);
        if (contextOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Capability not found");
        }

        var context = contextOpt.get();
        if (!context.isUsable()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Capability is expired or revoked");
        }

        byte[] pngBytes;
        if ("direct".equalsIgnoreCase(format)) {
            pngBytes = capabilityService.generateDirectUrlQrPng(token);
        } else {
            // Default to canonical strongly typed QR payload
            var cap = new VerificationCapability(
                    context.capabilityId(),
                    context.capabilityToken(),
                    context.credentialId(),
                    context.documentId(),
                    context.mode(),
                    QrVerificationPayload.PROTOCOL_VERSION_V1,
                    context.status(),
                    authentication != null ? authentication.getName() : "system",
                    java.time.Instant.now(),
                    context.expiresAt(),
                    null,
                    null
            );
            pngBytes = capabilityService.generateQrCodePng(cap);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store, no-cache, must-revalidate");
        headers.setPragma("no-cache");

        return ResponseEntity.ok().headers(headers).contentType(MediaType.IMAGE_PNG).body(pngBytes);
    }

    /**
     * Revokes a capability.
     */
    @PostMapping("/{id}/revoke")
    public ResponseEntity<Void> revokeCapability(
            @PathVariable UUID id,
            @RequestParam(name = "reason", required = false) String reason,
            Authentication authentication) {
        boolean revoked = capabilityService.revokeCapability(CapabilityId.of(id), reason);
        if (!revoked) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Capability not found");
        }
        verificationAuditService.record(authentication, "QR_CAPABILITY_REVOKED", null, null, true);
        return ResponseEntity.noContent().build();
    }

    public record CreateCapabilityRequest(
            UUID credentialId,
            UUID documentId,
            VerificationMode mode,
            Long ttlSeconds
    ) {}

    public record CapabilityResponse(
            UUID id,
            String capabilityToken,
            VerificationMode mode,
            String protocolVersion,
            CapabilityStatus status,
            String expiresAt
    ) {
        public static CapabilityResponse from(VerificationCapability c) {
            return new CapabilityResponse(
                    c.id().value(),
                    c.capabilityToken(),
                    c.mode(),
                    c.protocolVersion(),
                    c.status(),
                    c.expiresAt().toString()
            );
        }
    }
}
