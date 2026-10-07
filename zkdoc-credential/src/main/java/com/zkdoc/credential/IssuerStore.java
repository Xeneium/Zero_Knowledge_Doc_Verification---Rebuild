package com.zkdoc.credential;

import java.util.Optional;

/**
 * Persistence port for managing authoritative Issuer domain entities.
 */
public interface IssuerStore {
    void save(Issuer issuer);

    Optional<Issuer> findById(IssuerId id);

    void updateStatus(IssuerId id, IssuerStatus status);
}
