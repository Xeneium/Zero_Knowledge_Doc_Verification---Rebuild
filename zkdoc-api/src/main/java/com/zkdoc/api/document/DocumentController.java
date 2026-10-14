package com.zkdoc.api.document;

import com.zkdoc.api.audit.VerificationAuditService;
import com.zkdoc.document.DocumentRecord;
import com.zkdoc.document.DocumentNotFoundException;
import com.zkdoc.document.DocumentUploadService;
import com.zkdoc.document.VerificationResult;
import java.io.IOException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentUploadService documentUploadService;
    private final QrCodeService qrCodeService;
    private final VerificationAuditService verificationAuditService;
    private final com.zkdoc.api.security.ObjectAuthorizationService objectAuthorizationService;
    private final com.zkdoc.api.metrics.ZkdocMetrics zkdocMetrics;
    private final String publicVerificationBaseUrl;

    public DocumentController(
            DocumentUploadService documentUploadService,
            QrCodeService qrCodeService,
            VerificationAuditService verificationAuditService,
            com.zkdoc.api.security.ObjectAuthorizationService objectAuthorizationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.zkdoc.api.metrics.ZkdocMetrics zkdocMetrics,
            @Value("${zkdoc.public-verification-base-url}") String publicVerificationBaseUrl) {
        this.documentUploadService = documentUploadService;
        this.qrCodeService = qrCodeService;
        this.verificationAuditService = verificationAuditService;
        this.objectAuthorizationService = objectAuthorizationService;
        this.zkdocMetrics = zkdocMetrics;
        this.publicVerificationBaseUrl = publicVerificationBaseUrl.replaceAll("/+$", "");
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {
        if (file == null || file.isEmpty()) {
            if (zkdocMetrics != null) {
                zkdocMetrics.recordDocumentUpload(false, null);
            }
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Upload file must not be empty");
        }
        long start = System.nanoTime();
        try {
            String ownerId = (authentication != null && authentication.getName() != null) ? authentication.getName() : "system";
            DocumentRecord document = documentUploadService.upload(
                    file.getBytes(), file.getOriginalFilename(), file.getContentType(), ownerId);
            verificationAuditService.record(authentication, "DOCUMENT_UPLOADED", null, document.id(), true);
            if (zkdocMetrics != null) {
                zkdocMetrics.recordDocumentUpload(true, java.time.Duration.ofNanos(System.nanoTime() - start));
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new UploadResponse(document.id(), HexFormat.of().formatHex(document.digest())));
        } catch (Exception e) {
            if (zkdocMetrics != null) {
                zkdocMetrics.recordDocumentUpload(false, java.time.Duration.ofNanos(System.nanoTime() - start));
            }
            throw e;
        }
    }

    @GetMapping("/{id}/verify")
    public VerificationResult verifyStoredCopy(@PathVariable UUID id, Authentication authentication) {
        try {
            VerificationResult result = documentUploadService.verifyStoredCopy(id);
            verificationAuditService.record(authentication, "DOCUMENT_SERVER_COPY_VERIFY", null, id, result.verified());
            return result;
        } catch (DocumentNotFoundException exception) {
            verificationAuditService.record(authentication, "DOCUMENT_SERVER_COPY_VERIFY", null, id, false);
            throw exception;
        }
    }

    @PostMapping(value = "/{id}/verify-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VerificationResult verifyUploadedCopy(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
            Authentication authentication)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Verification copy file must not be empty");
        }
        try {
            VerificationResult result = documentUploadService.verifyUploadedCopy(id, file.getBytes());
            verificationAuditService.record(authentication, "DOCUMENT_UPLOADED_COPY_VERIFY", null, id, result.verified());
            return result;
        } catch (DocumentNotFoundException exception) {
            verificationAuditService.record(authentication, "DOCUMENT_UPLOADED_COPY_VERIFY", null, id, false);
            throw exception;
        }
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] publicVerificationQrCode(@PathVariable UUID id, Authentication authentication) {
        objectAuthorizationService.checkDocumentAccess(authentication, id);
        String token = documentUploadService.publicVerificationToken(id);
        return qrCodeService.pngFor(publicVerificationBaseUrl + "/api/public/verify/" + token);
    }

    public record UploadResponse(UUID id, String digest) {
    }
}
