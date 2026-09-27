package com.zkdoc.zkp;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Production source: every candidate blinding factor is filled by SecureRandom. */
@Component
public class SecureRandomBlindingFactorSource implements BlindingFactorSource {
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void nextBytes(byte[] destination) {
        secureRandom.nextBytes(destination);
    }
}
