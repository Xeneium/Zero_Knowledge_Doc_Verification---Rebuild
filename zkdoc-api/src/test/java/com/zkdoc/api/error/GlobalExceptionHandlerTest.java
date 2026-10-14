package com.zkdoc.api.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test-endpoint");
    }

    @Test
    @DisplayName("handleMethodArgumentNotValidException maps field errors cleanly")
    void handleMethodArgumentNotValidException() throws Exception {
        Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "attributeName", "must not be blank"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).isEqualTo("Request validation failed");
        assertThat(response.getBody().validationErrors()).hasSize(1);
        assertThat(response.getBody().validationErrors().getFirst().field()).isEqualTo("attributeName");
        assertThat(response.getBody().validationErrors().getFirst().message()).isEqualTo("must not be blank");
    }

    @Test
    @DisplayName("handleMessageNotReadable maps malformed JSON to safe 400 Bad Request")
    void handleMessageNotReadable() {
        HttpInputMessage inputMessage = new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return InputStream.nullInputStream();
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        };
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("JSON parse error: Unexpected character", inputMessage);

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleMessageNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Malformed JSON or invalid request payload");
        assertThat(response.getBody().message()).doesNotContain("Unexpected character");
    }

    @Test
    @DisplayName("handleTypeMismatch maps mismatched path or query parameter to safe 400 Bad Request")
    void handleTypeMismatch() throws Exception {
        Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "invalid-val", String.class, "credentialId", parameter, new IllegalArgumentException());

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid parameter type or format for 'credentialId'");
    }

    @Test
    @DisplayName("handleMissingParameter maps missing parameter to safe 400 Bad Request")
    void handleMissingParameter() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("file", "MultipartFile");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleMissingParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Missing required parameter: file");
    }

    @Test
    @DisplayName("handleMethodNotSupported maps method mismatch to 405 Method Not Allowed")
    void handleMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PUT");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleMethodNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(405);
        assertThat(response.getBody().message()).isEqualTo("HTTP method 'PUT' is not supported for this endpoint");
    }

    @Test
    @DisplayName("handleMediaTypeNotSupported maps unsupported content type to 415")
    void handleMediaTypeNotSupported() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, java.util.List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleMediaTypeNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(415);
        assertThat(response.getBody().message()).isEqualTo("Media type 'text/plain' is not supported");
    }

    @Test
    @DisplayName("handleMultipartException maps multipart parsing error to 400 Bad Request")
    void handleMultipartException() {
        MultipartException ex = new MultipartException("Current request is not a multipart request");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleMultipartException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid multipart request or payload");
    }

    @Test
    @DisplayName("handleDataIntegrityViolation scrubs database details and returns 409 Conflict")
    void handleDataIntegrityViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("UNIQUE constraint failed: credentials.id");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().message()).isEqualTo("Operation violates a data integrity or uniqueness constraint");
        assertThat(response.getBody().message()).doesNotContain("credentials.id").doesNotContain("UNIQUE constraint");
    }

    @Test
    @DisplayName("handleAccessDenied maps to 403 Forbidden without leaking authorization internals")
    void handleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Access denied by authority check");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Access is denied");
    }

    @Test
    @DisplayName("handleUnexpectedException masks internal exception message with correlation reference")
    void handleUnexpectedException() {
        RuntimeException ex = new RuntimeException("Null pointer in secret cryptographic calculation");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleUnexpectedException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().message()).contains("An unexpected internal error occurred. Request reference:");
        assertThat(response.getBody().message()).doesNotContain("cryptographic calculation").doesNotContain("Null pointer");
    }

    private void dummyMethod(String param) {
    }
}
