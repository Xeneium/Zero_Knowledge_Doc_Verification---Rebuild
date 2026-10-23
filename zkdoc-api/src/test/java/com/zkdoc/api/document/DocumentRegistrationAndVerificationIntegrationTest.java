package com.zkdoc.api.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkdoc.api.audit.JdbcSecurityAuditService;
import com.zkdoc.api.audit.SecurityAuditService;
import com.zkdoc.api.audit.VerificationAuditService;
import com.zkdoc.api.error.GlobalExceptionHandler;
import com.zkdoc.api.publicverify.PublicVerificationController;
import com.zkdoc.api.qr.JdbcVerificationCapabilityStore;
import com.zkdoc.api.qr.VerificationCapabilityStore;
import com.zkdoc.api.security.ObjectAuthorizationService;
import com.zkdoc.credential.CredentialStore;
import com.zkdoc.credential.HolderCredentialStore;
import com.zkdoc.credential.JdbcCredentialStore;
import com.zkdoc.credential.JdbcHolderCredentialStore;
import com.zkdoc.document.DocumentRecord;
import com.zkdoc.document.DocumentStore;
import com.zkdoc.document.DocumentUploadService;
import com.zkdoc.document.JdbcDocumentStore;
import com.zkdoc.document.RsaPssSigner;
import com.zkdoc.document.Sha256Hasher;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("End-to-End Document Registration, QR Generation & Direct Verification Integration Tests")
class DocumentRegistrationAndVerificationIntegrationTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;
    private DocumentUploadService documentUploadService;
    private DocumentStore documentStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Authentication issuerAuth = new UsernamePasswordAuthenticationToken(
            "university-issuer", null, List.of(new SimpleGrantedAuthority("ROLE_ISSUER")));

    private final Authentication verifierAuth = new UsernamePasswordAuthenticationToken(
            "employer-verifier", null, List.of(new SimpleGrantedAuthority("ROLE_VERIFIER")));

    @BeforeEach
    void setUp() {
        String dbUrl = "jdbc:sqlite:" + tempDir.resolve("zkdoc_test.db").toAbsolutePath();
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(dbUrl);

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/sqlite")
                .load();
        flyway.migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        documentStore = new JdbcDocumentStore(jdbcTemplate);
        CredentialStore credentialStore = new JdbcCredentialStore(jdbcTemplate);
        HolderCredentialStore holderCredentialStore = new JdbcHolderCredentialStore(jdbcTemplate);
        VerificationCapabilityStore capabilityStore = new JdbcVerificationCapabilityStore(jdbcTemplate);

        Sha256Hasher hasher = new Sha256Hasher();
        RsaPssSigner signer = new RsaPssSigner();
        documentUploadService = new DocumentUploadService(documentStore, hasher, signer);

        SecurityAuditService auditService = new JdbcSecurityAuditService(jdbcTemplate, Clock.systemUTC());
        VerificationAuditService verificationAuditService = new VerificationAuditService(jdbcTemplate, auditService);
        ObjectAuthorizationService objectAuthorizationService = new ObjectAuthorizationService(
                documentStore, credentialStore, holderCredentialStore, capabilityStore, auditService);
        QrCodeService qrCodeService = new QrCodeService();

        String baseUrl = "https://zkdoc.example.com";
        DocumentController documentController = new DocumentController(
                documentUploadService, qrCodeService, verificationAuditService, objectAuthorizationService, null, baseUrl);
        PublicVerificationController publicVerificationController = new PublicVerificationController(
                documentUploadService, verificationAuditService, null, null, null);

        mockMvc = MockMvcBuilders
                .standaloneSetup(documentController, publicVerificationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Workflow A: Upload document without QR code registers metadata, SHA-256 digest, and QR capability")
    void uploadAndRegisterDocumentSuccessfully() throws Exception {
        byte[] pdfBytes = "%PDF-1.7\nOfficial Degree Certificate for Jane Doe\n%%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "degree_certificate.pdf", "application/pdf", pdfBytes);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .principal(issuerAuth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.digest", notNullValue()))
                .andExpect(jsonPath("$.publicToken", notNullValue()))
                .andExpect(jsonPath("$.verificationUrl", notNullValue()))
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        UUID docId = UUID.fromString(responseNode.get("id").asText());
        String token = responseNode.get("publicToken").asText();
        String verificationUrl = responseNode.get("verificationUrl").asText();

        assertTrue(verificationUrl.contains(token));

        // Verify document QR code generation returns valid PNG image bytes
        mockMvc.perform(get("/api/documents/{id}/qr", docId)
                        .principal(issuerAuth))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG_VALUE))
                .andExpect(result -> {
                    byte[] png = result.getResponse().getContentAsByteArray();
                    assertTrue(png.length > 50, "QR PNG bytes should be non-trivial");
                    // Check PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
                    assertEquals((byte) 0x89, png[0]);
                    assertEquals((byte) 0x50, png[1]);
                    assertEquals((byte) 0x4E, png[2]);
                    assertEquals((byte) 0x47, png[3]);
                });
    }

    @Test
    @DisplayName("Workflow B: Verifier scans QR code or follows public link to verify document token")
    void verifyViaPublicQrToken() throws Exception {
        byte[] pdfBytes = "%PDF-1.7\nAccreditation Transcript\n%%EOF".getBytes(StandardCharsets.UTF_8);
        DocumentRecord record = documentUploadService.upload(
                pdfBytes, "transcript.pdf", "application/pdf", "university-issuer");

        // Verify publicly via token without authentication
        mockMvc.perform(get("/api/public/verify/{token}", record.publicToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signatureValid", is(true)))
                .andExpect(jsonPath("$.documentActive", is(true)));
    }

    @Test
    @DisplayName("Workflow C: Verifier directly uploads document without QR or UUID -> verified by digest")
    void directUploadVerificationWithoutUuidMatchesByDigest() throws Exception {
        byte[] pdfBytes = "%PDF-1.7\nDegree Certificate Issued to Graduate\n%%EOF".getBytes(StandardCharsets.UTF_8);
        DocumentRecord record = documentUploadService.upload(
                pdfBytes, "degree.pdf", "application/pdf", "university-issuer");

        // Verifier directly uploads candidate file to public verification endpoint without UUID
        MockMultipartFile verifyFile = new MockMultipartFile(
                "file", "degree_candidate.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/public/verify/upload")
                        .file(verifyFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("VERIFIED")))
                .andExpect(jsonPath("$.verified", is(true)))
                .andExpect(jsonPath("$.documentId", is(record.id().toString())))
                .andExpect(jsonPath("$.signatureValid", is(true)))
                .andExpect(jsonPath("$.documentActive", is(true)));
    }

    @Test
    @DisplayName("Workflow C: Direct upload of altered document without UUID returns NOT_FOUND")
    void directUploadVerificationOfAlteredDocumentWithoutUuidReturnsNotFound() throws Exception {
        byte[] originalBytes = "%PDF-1.7\nAuthentic Certificate\n%%EOF".getBytes(StandardCharsets.UTF_8);
        documentUploadService.upload(originalBytes, "cert.pdf", "application/pdf", "university-issuer");

        byte[] tamperedBytes = "%PDF-1.7\nAuthentic Certificate (Grade Altered)\n%%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile tamperedFile = new MockMultipartFile(
                "file", "cert.pdf", "application/pdf", tamperedBytes);

        mockMvc.perform(multipart("/api/public/verify/upload")
                        .file(tamperedFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NOT_FOUND")))
                .andExpect(jsonPath("$.verified", is(false)))
                .andExpect(jsonPath("$.signatureValid", is(false)));
    }

    @Test
    @DisplayName("Workflow C: Direct upload of altered document with specific UUID returns INTEGRITY_MISMATCH")
    void directUploadVerificationWithSpecificUuidReturnsIntegrityMismatch() throws Exception {
        byte[] originalBytes = "%PDF-1.7\nDiploma Certificate\n%%EOF".getBytes(StandardCharsets.UTF_8);
        DocumentRecord record = documentUploadService.upload(
                originalBytes, "diploma.pdf", "application/pdf", "university-issuer");

        byte[] modifiedBytes = "%PDF-1.7\nDiploma Certificate with GPA altered\n%%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile modifiedFile = new MockMultipartFile(
                "file", "diploma.pdf", "application/pdf", modifiedBytes);

        mockMvc.perform(multipart("/api/public/verify/upload")
                        .file(modifiedFile)
                        .param("documentId", record.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INTEGRITY_MISMATCH")))
                .andExpect(jsonPath("$.verified", is(false)))
                .andExpect(jsonPath("$.documentId", is(record.id().toString())))
                .andExpect(jsonPath("$.signatureValid", is(false)));
    }

    @Test
    @DisplayName("Workflow C: Authenticated verifier uploads candidate copy via /api/documents/verify-upload")
    void authenticatedVerifierDirectUploadVerification() throws Exception {
        byte[] pdfBytes = "%PDF-1.7\nOfficial Identity Document\n%%EOF".getBytes(StandardCharsets.UTF_8);
        DocumentRecord record = documentUploadService.upload(
                pdfBytes, "id.pdf", "application/pdf", "university-issuer");

        MockMultipartFile file = new MockMultipartFile(
                "file", "id_copy.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/documents/verify-upload")
                        .file(file)
                        .principal(verifierAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("VERIFIED")))
                .andExpect(jsonPath("$.verified", is(true)))
                .andExpect(jsonPath("$.documentId", is(record.id().toString())));
    }

    @Test
    @DisplayName("Registered documents listing returns caller documents with public tokens and verification URLs")
    void listDocumentsForAuthenticatedIssuer() throws Exception {
        byte[] doc1 = "%PDF-1.7\nDocument 1\n%%EOF".getBytes(StandardCharsets.UTF_8);
        byte[] doc2 = "%PDF-1.7\nDocument 2\n%%EOF".getBytes(StandardCharsets.UTF_8);

        documentUploadService.upload(doc1, "doc1.pdf", "application/pdf", "university-issuer");
        documentUploadService.upload(doc2, "doc2.pdf", "application/pdf", "university-issuer");

        mockMvc.perform(get("/api/documents")
                        .principal(issuerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].publicToken", notNullValue()))
                .andExpect(jsonPath("$[0].verificationUrl", notNullValue()))
                .andExpect(jsonPath("$[0].digest", notNullValue()))
                .andExpect(jsonPath("$[0].active", is(true)));
    }
}
