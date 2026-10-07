package com.zkdoc.api.publicverify;

import com.zkdoc.api.audit.VerificationAuditService;
import com.zkdoc.api.qr.CapabilityVerificationContext;
import com.zkdoc.api.qr.VerificationCapabilityService;
import com.zkdoc.api.qr.VerificationMode;
import com.zkdoc.credential.CredentialService;
import com.zkdoc.credential.CredentialVerificationResult;
import com.zkdoc.document.DocumentNotFoundException;
import com.zkdoc.document.DocumentUploadService;
import com.zkdoc.document.PublicVerificationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Public verification endpoint resolving opaque QR tokens and verification capabilities.
 * Enforces strict minimal disclosure: returns only validity, mode, and protocol version.
 * Never leaks raw documents, private attributes, proving material, or internal secrets.
 */
@RestController
@RequestMapping("/api/public/verify")
public class PublicVerificationController {

    private final DocumentUploadService documentUploadService;
    private final VerificationAuditService verificationAuditService;
    private final VerificationCapabilityService capabilityService;
    private final CredentialService credentialService;

    @Autowired
    public PublicVerificationController(
            DocumentUploadService documentUploadService,
            VerificationAuditService verificationAuditService,
            @Autowired(required = false) VerificationCapabilityService capabilityService,
            @Autowired(required = false) CredentialService credentialService) {
        this.documentUploadService = documentUploadService;
        this.verificationAuditService = verificationAuditService;
        this.capabilityService = capabilityService;
        this.credentialService = credentialService;
    }

    @GetMapping("/{token}")
    public PublicVerificationResult verify(@PathVariable String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            verificationAuditService.record(null, "PUBLIC_VERIFICATION_ATTEMPT", null, null, false);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token format");
        }

        // 1. Check if token maps to a Phase 11 VerificationCapability
        if (capabilityService != null) {
            Optional<CapabilityVerificationContext> ctxOpt = capabilityService.resolveContext(token);
            if (ctxOpt.isPresent()) {
                CapabilityVerificationContext ctx = ctxOpt.get();
                if (!ctx.isUsable()) {
                    verificationAuditService.record(null, "QR_VERIFICATION_FAILURE", ctx.credentialId(), ctx.documentId(), false);
                    return new PublicVerificationResult(false, false);
                }

                // If capability references a credential:
                if (ctx.credentialId() != null && credentialService != null) {
                    CredentialVerificationResult credResult = credentialService.verify(ctx.credentialId());
                    boolean success = credResult.verified();
                    verificationAuditService.record(null, "QR_VERIFICATION_SUCCESS", ctx.credentialId(), null, success);
                    return new PublicVerificationResult(credResult.signatureValid(), credResult.operationalStatusValid() && success);
                }

                // If capability references a document:
                if (ctx.documentId() != null) {
                    var docResult = documentUploadService.verifyStoredCopy(ctx.documentId());
                    boolean success = docResult.verified();
                    verificationAuditService.record(null, "QR_VERIFICATION_SUCCESS", null, ctx.documentId(), success);
                    return new PublicVerificationResult(success, success);
                }
            }
        }

        // 2. Fall back to direct document publicToken verification (Mode A / backwards compatibility)
        try {
            PublicVerificationResult result = documentUploadService.verifyPublicToken(token);
            verificationAuditService.record(null, "PUBLIC_DOCUMENT_VERIFY", null, null,
                    result.signatureValid() && result.documentActive());
            return result;
        } catch (DocumentNotFoundException exception) {
            verificationAuditService.record(null, "PUBLIC_VERIFICATION_FAILURE", null, null, false);
            throw exception;
        }
    }

    @GetMapping("/capability/{token}")
    public CapabilityPublicVerificationResponse verifyCapabilityDetails(@PathVariable String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token format");
        }
        if (capabilityService == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Capability verification not enabled");
        }

        Optional<CapabilityVerificationContext> ctxOpt = capabilityService.resolveContext(token);
        if (ctxOpt.isEmpty()) {
            verificationAuditService.record(null, "QR_VERIFICATION_FAILURE", null, null, false);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification capability not found");
        }

        CapabilityVerificationContext ctx = ctxOpt.get();
        if (!ctx.isUsable()) {
            verificationAuditService.record(null, "QR_VERIFICATION_FAILURE", ctx.credentialId(), ctx.documentId(), false);
            return CapabilityPublicVerificationResponse.failure(ctx.mode().name(), "QR_V1", "Capability expired or revoked");
        }

        if (ctx.credentialId() != null && credentialService != null) {
            CredentialVerificationResult credResult = credentialService.verify(ctx.credentialId());
            if (credResult.verified()) {
                verificationAuditService.record(null, "QR_VERIFICATION_SUCCESS", ctx.credentialId(), null, true);
                return CapabilityPublicVerificationResponse.success(ctx.mode().name(), "QR_V1");
            } else {
                verificationAuditService.record(null, "QR_VERIFICATION_FAILURE", ctx.credentialId(), null, false);
                return CapabilityPublicVerificationResponse.failure(ctx.mode().name(), "QR_V1", "Cryptographic verification failed");
            }
        }

        if (ctx.documentId() != null) {
            var docResult = documentUploadService.verifyStoredCopy(ctx.documentId());
            if (docResult.verified()) {
                verificationAuditService.record(null, "QR_VERIFICATION_SUCCESS", null, ctx.documentId(), true);
                return CapabilityPublicVerificationResponse.success(ctx.mode().name(), "QR_V1");
            } else {
                verificationAuditService.record(null, "QR_VERIFICATION_FAILURE", null, ctx.documentId(), false);
                return CapabilityPublicVerificationResponse.failure(ctx.mode().name(), "QR_V1", "Document verification failed");
            }
        }

        return CapabilityPublicVerificationResponse.failure(ctx.mode().name(), "QR_V1", "Unbound capability");
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Void> unknownToken() {
        verificationAuditService.record(null, "PUBLIC_DOCUMENT_VERIFY", null, null, false);
        return ResponseEntity.notFound().build();
    }
}
