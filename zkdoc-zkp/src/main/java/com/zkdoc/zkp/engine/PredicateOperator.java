package com.zkdoc.zkp.engine;

import java.util.Objects;

/**
 * Explicit, strongly-typed operator for cryptographic predicate evaluations.
 */
public enum PredicateOperator {
    GREATER_THAN_OR_EQUAL(">=");

    private final String symbol;

    PredicateOperator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
