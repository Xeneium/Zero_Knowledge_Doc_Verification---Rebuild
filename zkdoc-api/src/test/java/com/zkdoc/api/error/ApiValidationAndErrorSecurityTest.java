package com.zkdoc.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkdoc.api.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
class ApiValidationAndErrorSecurityTest {

    private static final Path DATABASE_FILE = newDatabaseFile();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE.toAbsolutePath());
    }

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-api-val-it-").resolve("zkdoc.db");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create temp db", e);
        }
    }

    @Nested
    @DisplayName("Malformed JSON & Request Body Security")
    class MalformedBodyTests {

        @Test
        @DisplayName("Malformed JSON body returns 400 Bad Request with sanitized ApiErrorResponse")
        @WithMockUser(roles = "ISSUER")
        void malformedJsonReturns400() throws Exception {
            mockMvc.perform(post("/api/credentials/issue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"issuerId\": \"issuer-1\", malformed_json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Malformed JSON or invalid request payload"))
                    .andExpect(jsonPath("$.correlationId").exists())
                    .andExpect(header().exists("X-Correlation-ID"));
        }

        @Test
        @DisplayName("Missing request body returns 400 Bad Request with sanitized message")
        @WithMockUser(roles = "ISSUER")
        void missingRequestBodyReturns400() throws Exception {
            mockMvc.perform(post("/api/credentials/issue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Malformed JSON or invalid request payload"));
        }
    }

    @Nested
    @DisplayName("Type Mismatch and Missing Parameter Security")
    class ParameterMismatchTests {

        @Test
        @DisplayName("Invalid UUID path variable returns 400 Bad Request without leaking stack trace")
        @WithMockUser(roles = "VERIFIER")
        void invalidUuidPathVariableReturns400() throws Exception {
            mockMvc.perform(get("/api/documents/not-a-valid-uuid/verify"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value(containsString("Invalid parameter type or format for 'id'")))
                    .andExpect(jsonPath("$.correlationId").exists());
        }
    }

    @Nested
    @DisplayName("HTTP Method and Media Type Boundary Enforcement")
    class HttpBoundarySecurityTests {

        @Test
        @DisplayName("Unsupported HTTP method returns 405 Method Not Allowed")
        @WithMockUser(roles = "ISSUER")
        void unsupportedMethodReturns405() throws Exception {
            mockMvc.perform(put("/api/credentials/issue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                    .andExpect(jsonPath("$.message").value(containsString("HTTP method 'PUT' is not supported")));
        }

        @Test
        @DisplayName("Unsupported media type returns 415 Unsupported Media Type")
        @WithMockUser(roles = "ISSUER")
        void unsupportedMediaTypeReturns415() throws Exception {
            mockMvc.perform(post("/api/credentials/issue")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("Hello World"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.status").value(415))
                    .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                    .andExpect(jsonPath("$.message").value(containsString("not supported")));
        }
    }

    @Nested
    @DisplayName("Multipart Document Upload Boundary Security")
    class MultipartUploadValidationTests {

        @Test
        @DisplayName("Empty file upload is rejected with 400 Bad Request")
        @WithMockUser(username = "issuer-1", roles = "ISSUER")
        void emptyFileUploadRejected() throws Exception {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file", "empty.pdf", "application/pdf", new byte[0]);

            mockMvc.perform(multipart("/api/documents").file(emptyFile))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Upload file must not be empty"));
        }

        @Test
        @DisplayName("Empty verification copy file upload is rejected with 400 Bad Request")
        @WithMockUser(username = "verifier-1", roles = "VERIFIER")
        void emptyVerifyFileUploadRejected() throws Exception {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file", "empty.pdf", "application/pdf", new byte[0]);

            mockMvc.perform(multipart("/api/documents/00000000-0000-0000-0000-000000000001/verify-upload")
                            .file(emptyFile))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Verification copy file must not be empty"));
        }
    }

    @Nested
    @DisplayName("Bean Validation & Constraints on Request DTOs")
    class DtoBeanValidationTests {

        @Test
        @DisplayName("IssueCredentialRequest with missing/blank required fields returns 400 with field errors")
        @WithMockUser(username = "issuer-1", roles = "ISSUER")
        void issueCredentialMissingRequiredFieldsReturns400() throws Exception {
            Map<String, Object> badPayload = Map.of(
                    "issuerId", "", // blank
                    "schemaId", "schema-1",
                    "issuerKeyId", "key-1",
                    "attributes", List.of() // empty
            );

            mockMvc.perform(post("/api/credentials/issue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badPayload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.validationErrors").isArray());
        }

        @Test
        @DisplayName("IssueCredentialRequest with oversized attribute fields returns 400")
        @WithMockUser(username = "issuer-1", roles = "ISSUER")
        void issueCredentialOversizedAttributesReturns400() throws Exception {
            String longName = "A".repeat(200); // exceeds max 128
            Map<String, Object> badPayload = Map.of(
                    "issuerId", "issuer-1",
                    "schemaId", "schema-1",
                    "issuerKeyId", "key-1",
                    "attributes", List.of(Map.of(
                            "name", longName,
                            "value", "100",
                            "type", "INTEGER"
                    ))
            );

            mockMvc.perform(post("/api/credentials/issue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badPayload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors[?(@.field == 'attributes[0].name')]").exists());
        }

        @Test
        @DisplayName("GeneratePredicateProofRequest invalid nonce and operator returns 400")
        @WithMockUser(username = "holder-1", roles = "HOLDER")
        void generatePredicateProofInvalidFieldsReturns400() throws Exception {
            Map<String, Object> badPayload = Map.of(
                    "holderCredentialId", "00000000-0000-0000-0000-000000000001",
                    "attributeName", "age",
                    "operator", "INVALID_OPERATOR", // pattern mismatch
                    "threshold", -5, // negative threshold
                    "nonceHex", "not-hex-!!!" // pattern mismatch
            );

            mockMvc.perform(post("/api/holder/credentials/generate-predicate-proof")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badPayload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Request validation failed"));
        }

        @Test
        @DisplayName("Verification Capability request with negative or zero TTL returns 400")
        @WithMockUser(username = "holder-1", roles = "HOLDER")
        void createCapabilityInvalidTtlReturns400() throws Exception {
            Map<String, Object> badPayload = Map.of(
                    "credentialId", "00000000-0000-0000-0000-000000000001",
                    "mode", "ONE_TIME",
                    "ttlSeconds", -10 // non-positive
            );

            mockMvc.perform(post("/api/capabilities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badPayload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Admin Key Registration with blank keyId or invalid PEM returns 400")
        @WithMockUser(username = "admin-1", roles = "ADMIN")
        void keyRegistrationInvalidReturns400() throws Exception {
            Map<String, Object> badPayload = Map.of(
                    "keyId", "", // blank
                    "purpose", "CREDENTIAL_SIGNING",
                    "publicKeyPem", "" // blank
            );

            mockMvc.perform(post("/api/admin/keys/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badPayload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Request validation failed"));
        }
    }

    @Nested
    @DisplayName("Information Leakage Prevention")
    class InformationDisclosureSecurityTests {

        @Test
        @DisplayName("Error response never exposes SQL keywords, table schemas, or internal exceptions")
        @WithMockUser(roles = "ISSUER")
        void errorResponseNeverLeaksInternalDetails() throws Exception {
            String malformedJson = "{ \"unexpected\": 12345, \"bad\": ";

            mockMvc.perform(post("/api/credentials/issue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", not(containsString("Exception"))))
                    .andExpect(jsonPath("$.message", not(containsString("SQL"))))
                    .andExpect(jsonPath("$.message", not(containsString("database"))))
                    .andExpect(jsonPath("$.message", not(containsString("org.springframework"))));
        }
    }
}
