package com.zkdoc.api.publicverify;

import com.zkdoc.api.audit.VerificationAuditService;
import com.zkdoc.document.DocumentUploadService;
import com.zkdoc.document.DocumentNotFoundException;
import com.zkdoc.document.PublicVerificationResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/verify")
public class PublicVerificationController {
    private final DocumentUploadService documentUploadService;
    private final VerificationAuditService verificationAuditService;

    public PublicVerificationController(DocumentUploadService documentUploadService,
            VerificationAuditService verificationAuditService) {
        this.documentUploadService = documentUploadService;
        this.verificationAuditService = verificationAuditService;
    }

    @GetMapping("/{token}")
    public PublicVerificationResult verify(@PathVariable String token) {
        PublicVerificationResult result = documentUploadService.verifyPublicToken(token);
        verificationAuditService.record(null, "PUBLIC_DOCUMENT_VERIFY", null, null,
                result.signatureValid() && result.documentActive());
        return result;
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Void> unknownToken() {
        verificationAuditService.record(null, "PUBLIC_DOCUMENT_VERIFY", null, null, false);
        return ResponseEntity.notFound().build();
    }
}
