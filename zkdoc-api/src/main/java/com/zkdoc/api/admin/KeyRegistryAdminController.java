package com.zkdoc.api.admin;

import com.zkdoc.keys.KeyAlgorithmPolicy;
import com.zkdoc.keys.KeyFingerprint;
import com.zkdoc.keys.KeyOwnerType;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistry;
import com.zkdoc.keys.KeyRegistryService;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN-only API for cryptographic trust registry operations.
 * Manages public-key trust anchors and lifecycle states;
 * private key material is never accepted, processed, or returned.
 */
@RestController
@RequestMapping("/api/admin/keys")
public class KeyRegistryAdminController {

    private final KeyRegistryService keyRegistryService;
    private final KeyRegistry keyRegistry;

    public KeyRegistryAdminController(KeyRegistryService keyRegistryService, KeyRegistry keyRegistry) {
        this.keyRegistryService = keyRegistryService;
        this.keyRegistry = keyRegistry;
    }

    @PostMapping({"", "/register"})
    public ResponseEntity<KeyRegisteredResponse> register(@jakarta.validation.Valid @RequestBody KeyRegistrationRequest request) {
        byte[] der = RegisteredKey.decodePemToDer(request.publicKeyPem());
        String fingerprint = KeyFingerprint.fromDer(der).value();
        Instant now = Instant.now();

        String ownerId = request.ownerId() != null && !request.ownerId().isBlank() ? request.ownerId() : "platform";
        KeyOwnerType ownerType = request.ownerType() != null ? request.ownerType() : KeyOwnerType.PLATFORM;
        String algorithm = request.algorithm() != null ? request.algorithm() : KeyAlgorithmPolicy.MANDATED_ALGORITHM;
        KeyStatus status = request.status() != null ? request.status() : KeyStatus.ACTIVE;

        RegisteredKey key = new RegisteredKey(
                UUID.randomUUID(),
                request.keyId(),
                request.purpose(),
                status,
                algorithm,
                KeyAlgorithmPolicy.MANDATED_PARAMETERS,
                ownerId,
                ownerType,
                request.publicKeyPem(),
                der,
                "X.509/PEM",
                fingerprint,
                now,
                status == KeyStatus.ACTIVE ? now : null,
                null,
                null,
                null,
                null,
                1,
                Collections.emptyMap());

        keyRegistryService.register(key);
        return ResponseEntity.status(HttpStatus.CREATED).body(new KeyRegisteredResponse(key.id(), key.keyId()));
    }

    @GetMapping
    public ResponseEntity<List<KeyMetadataResponse>> listAllKeys() {
        List<KeyMetadataResponse> list = keyRegistry.findAll().stream()
                .map(k -> new KeyMetadataResponse(
                        k.id(),
                        k.keyId(),
                        k.purpose(),
                        k.status(),
                        k.ownerId(),
                        k.ownerType(),
                        k.algorithm(),
                        k.fingerprint(),
                        k.publicKeyPem(),
                        k.createdAt(),
                        k.activatedAt(),
                        k.retiredAt(),
                        k.revokedAt(),
                        k.replacementKeyId()))
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{keyId}")
    public ResponseEntity<KeyMetadataResponse> getKeyMetadata(@PathVariable String keyId) {
        return keyRegistry.findByKeyId(keyId)
                .map(k -> ResponseEntity.ok(new KeyMetadataResponse(
                        k.id(),
                        k.keyId(),
                        k.purpose(),
                        k.status(),
                        k.ownerId(),
                        k.ownerType(),
                        k.algorithm(),
                        k.fingerprint(),
                        k.publicKeyPem(),
                        k.createdAt(),
                        k.activatedAt(),
                        k.retiredAt(),
                        k.revokedAt(),
                        k.replacementKeyId())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{keyId}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable String keyId,
            @jakarta.validation.Valid @RequestBody KeyStatusChangeRequest request) {
        keyRegistryService.updateStatus(keyId, request.status(), request.reason(), request.replacementKeyId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{keyId}/rotate")
    public ResponseEntity<Void> rotateKey(
            @PathVariable String keyId,
            @jakarta.validation.Valid @RequestBody KeyRotateRequest request) {
        keyRegistryService.rotateKey(keyId, request.replacementKeyId());
        return ResponseEntity.noContent().build();
    }

    public record KeyRegistrationRequest(
            @jakarta.validation.constraints.NotBlank(message = "keyId must not be blank")
            @jakarta.validation.constraints.Size(max = 128, message = "keyId must not exceed 128 characters")
            String keyId,

            @jakarta.validation.constraints.NotNull(message = "purpose must not be null")
            KeyPurpose purpose,

            KeyStatus status,

            @jakarta.validation.constraints.Size(max = 64, message = "algorithm must not exceed 64 characters")
            String algorithm,

            @jakarta.validation.constraints.Size(max = 128, message = "ownerId must not exceed 128 characters")
            String ownerId,

            KeyOwnerType ownerType,

            @jakarta.validation.constraints.NotBlank(message = "publicKeyPem must not be blank")
            @jakarta.validation.constraints.Size(max = 8192, message = "publicKeyPem must not exceed 8192 characters")
            String publicKeyPem,

            @jakarta.validation.constraints.Size(max = 256, message = "privateKeyFileEnvironment must not exceed 256 characters")
            String privateKeyFileEnvironment) {
    }

    public record KeyStatusChangeRequest(
            @jakarta.validation.constraints.NotNull(message = "status must not be null")
            KeyStatus status,

            @jakarta.validation.constraints.Size(max = 512, message = "reason must not exceed 512 characters")
            String reason,

            @jakarta.validation.constraints.Size(max = 128, message = "replacementKeyId must not exceed 128 characters")
            String replacementKeyId) {
        public KeyStatusChangeRequest(KeyStatus status) {
            this(status, null, null);
        }
    }

    public record KeyRotateRequest(
            @jakarta.validation.constraints.Size(max = 128, message = "replacementKeyId must not exceed 128 characters")
            String replacementKeyId) {
    }

    public record KeyRegisteredResponse(UUID id, String keyId) {
    }

    public record KeyMetadataResponse(
            UUID id,
            String keyId,
            KeyPurpose purpose,
            KeyStatus status,
            String ownerId,
            KeyOwnerType ownerType,
            String algorithm,
            String fingerprint,
            String publicKeyPem,
            Instant createdAt,
            Instant activatedAt,
            Instant retiredAt,
            Instant revokedAt,
            String replacementKeyId) {
    }
}
