package com.zkdoc.keys;

import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class KeyRegistryService {
    private final KeyRegistry keyRegistry;

    public KeyRegistryService(KeyRegistry keyRegistry) {
        this.keyRegistry = keyRegistry;
    }

    public Optional<RegisteredKey> verificationKey(String keyId, KeyPurpose purpose) {
        return keyRegistry.findForVerification(keyId, purpose);
    }

    public Optional<RegisteredKey> activeSigningKey(KeyPurpose purpose) {
        return keyRegistry.findActiveForSigning(purpose);
    }

    public void register(RegisteredKey key) {
        keyRegistry.register(key);
    }

    public void updateStatus(String keyId, KeyStatus status) {
        keyRegistry.updateStatus(keyId, status);
    }
}
