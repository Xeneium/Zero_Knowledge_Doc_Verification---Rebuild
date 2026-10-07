package com.zkdoc.zkp.engine;

import java.util.Objects;

/**
 * Domain specification for an integer attribute predicate evaluated via ZKP.
 * <p>
 * Evaluates relations such as: {@code attributeName >= threshold} where
 * values are bounded non-negative integers in \([0, 2^{64}-1]\).
 */
public record AttributePredicate(
        String attributeName,
        PredicateOperator operator,
        long threshold
) {
    public AttributePredicate {
        Objects.requireNonNull(attributeName, "attributeName must not be null");
        if (attributeName.isBlank()) {
            throw new IllegalArgumentException("attributeName must not be blank");
        }
        Objects.requireNonNull(operator, "operator must not be null");
        if (threshold < 0) {
            throw new IllegalArgumentException("Negative threshold is not supported: " + threshold);
        }
    }

    public static AttributePredicate greaterThanOrEqual(String attributeName, long threshold) {
        return new AttributePredicate(attributeName, PredicateOperator.GREATER_THAN_OR_EQUAL, threshold);
    }

    /**
     * Verifies whether a given plaintext value satisfies this predicate in the integer domain.
     */
    public boolean satisfies(long value) {
        if (value < 0) return false;
        return switch (operator) {
            case GREATER_THAN_OR_EQUAL -> value >= threshold;
        };
    }
}
