package com.zkdoc.api.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zkdoc.api.Application;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
class SecurityAuthorizationIntegrationTest {
    private static final Path DATABASE_FILE = newDatabaseFile();
    @Autowired private MockMvc mockMvc;

    @DynamicPropertySource
    static void sqliteProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE.toAbsolutePath());
    }

    @Test
    @DisplayName("issuer is allowed to upload a document")
    @WithMockUser(username = "issuer", roles = "ISSUER")
    void issuerCanUploadDocument() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "example.txt", "text/plain", new byte[] {1})))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("holder is rejected from issuer-only document upload")
    @WithMockUser(username = "holder", roles = "HOLDER")
    void holderCannotUploadDocument() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "example.txt", "text/plain", new byte[] {1})))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("verifier is rejected from issuer-only document upload")
    @WithMockUser(username = "verifier", roles = "VERIFIER")
    void verifierCannotUploadDocument() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "example.txt", "text/plain", new byte[] {1})))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin is rejected from issuer-only document upload")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCannotUploadDocument() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "example.txt", "text/plain", new byte[] {1})))
                .andExpect(status().isForbidden());
    }

    private static Path newDatabaseFile() {
        try {
            return Files.createTempDirectory("zkdoc-security-it-").resolve("zkdoc-security.db");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create integration-test database path", exception);
        }
    }
}
