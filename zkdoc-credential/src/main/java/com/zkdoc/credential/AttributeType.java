package com.zkdoc.credential;

/**
 * Enumeration of explicitly supported credential attribute data types.
 * Cryptographic predicate range proofs and Pedersen commitments in Phase 0/1 are
 * restricted strictly to INTEGER attributes.
 */
public enum AttributeType {
    INTEGER(1, true),
    STRING(2, false),
    BOOLEAN(3, false),
    DATE(4, false),
    DATE_TIME(5, false),
    IDENTIFIER(6, false);

    private final int tag;
    private final boolean commitmentEligible;

    AttributeType(int tag, boolean commitmentEligible) {
        this.tag = tag;
        this.commitmentEligible = commitmentEligible;
    }

    public int tag() {
        return tag;
    }

    public boolean isCommitmentEligible() {
        return commitmentEligible;
    }
}
