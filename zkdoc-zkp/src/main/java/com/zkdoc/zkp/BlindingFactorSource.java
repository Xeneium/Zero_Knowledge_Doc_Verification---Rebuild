package com.zkdoc.zkp;

public interface BlindingFactorSource {
    void nextBytes(byte[] destination);
}
