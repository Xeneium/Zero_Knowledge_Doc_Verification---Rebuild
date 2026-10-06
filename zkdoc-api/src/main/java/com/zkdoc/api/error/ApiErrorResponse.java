package com.zkdoc.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standardized, production-safe API error response envelope.
 * Excludes null fields, stack traces, and internal implementation details.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<ValidationError> validationErrors
) {
    public record ValidationError(String field, String message) {}

    public static ApiErrorResponse of(int status, String error, String message, String path, String correlationId) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, correlationId, null);
    }

    public static ApiErrorResponse withValidation(int status, String error, String message, String path,
                                                  String correlationId, List<ValidationError> validationErrors) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, correlationId, validationErrors);
    }
}
