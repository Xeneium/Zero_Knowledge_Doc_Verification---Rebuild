package com.zkdoc.zkp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory reference implementation of {@link HolderProvingMaterialStore} for secure development and testing.
 * Enforces strict holder boundary isolation: records are indexed strictly by (holderId, credentialId, attributeName).
 * Redacts secret material in toString() and never logs raw bytes.
 */
@Component
public class InMemoryHolderProvingMaterialStore implements HolderProvingMaterialStore {

    private final Map<HolderSecretKey, AttributeOpeningSecret> store = new ConcurrentHashMap<>();

    @Override
    public void save(String holderId, AttributeOpeningSecret secret) {
        Objects.requireNonNull(holderId, "holderId must not be null");
        Objects.requireNonNull(secret, "secret must not be null");
        store.put(new HolderSecretKey(holderId, secret.credentialId(), secret.attributeName()), secret);
    }

    @Override
    public Optional<AttributeOpeningSecret> findByHolderAndCredentialAndAttribute(
            String holderId, UUID credentialId, String attributeName) {
        if (holderId == null || credentialId == null || attributeName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(new HolderSecretKey(holderId, credentialId, attributeName)));
    }

    @Override
    public List<AttributeOpeningSecret> findAllByHolderAndCredential(String holderId, UUID credentialId) {
        if (holderId == null || credentialId == null) {
            return List.of();
        }
        List<AttributeOpeningSecret> list = new ArrayList<>();
        store.forEach((k, v) -> {
            if (k.holderId().equals(holderId) && k.credentialId().equals(credentialId)) {
                list.add(v);
            }
        });
        return list;
    }

    @Override
    public void deleteByHolderAndCredential(String holderId, UUID credentialId) {
        if (holderId == null || credentialId == null) {
            return;
        }
        store.keySet().removeIf(k -> k.holderId().equals(holderId) && k.credentialId().equals(credentialId));
    }

    public void clear() {
        store.clear();
    }

    private record HolderSecretKey(String holderId, UUID credentialId, String attributeName) {}
}
