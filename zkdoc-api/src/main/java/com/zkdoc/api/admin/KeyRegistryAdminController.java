package com.zkdoc.api.admin;

import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistryService;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN-only API. It registers public-key metadata; private key material is never accepted. */
@RestController
@RequestMapping("/api/admin/keys")
public class KeyRegistryAdminController {
    private final KeyRegistryService keyRegistryService;

    public KeyRegistryAdminController(KeyRegistryService keyRegistryService) {
        this.keyRegistryService = keyRegistryService;
    }

    @PostMapping
    public ResponseEntity<KeyRegisteredResponse> register(@RequestBody KeyRegistrationRequest request) {
        RegisteredKey key = new RegisteredKey(UUID.randomUUID(), request.keyId(), request.purpose(), request.status(),
                request.algorithm(), request.publicKeyPem(), request.privateKeyFileEnvironment());
        keyRegistryService.register(key);
        return ResponseEntity.status(HttpStatus.CREATED).body(new KeyRegisteredResponse(key.id(), key.keyId()));
    }

    @PatchMapping("/{keyId}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable String keyId, @RequestBody KeyStatusChangeRequest request) {
        keyRegistryService.updateStatus(keyId, request.status());
        return ResponseEntity.noContent().build();
    }

    public record KeyRegistrationRequest(
            String keyId,
            KeyPurpose purpose,
            KeyStatus status,
            String algorithm,
            String publicKeyPem,
            String privateKeyFileEnvironment) {
    }

    public record KeyStatusChangeRequest(KeyStatus status) {
    }

    public record KeyRegisteredResponse(UUID id, String keyId) {
    }
}
