package com.zkdoc.api.credential;

import com.zkdoc.api.audit.VerificationAuditService;
import com.zkdoc.api.security.ObjectAuthorizationService;
import com.zkdoc.credential.ConcurrentLifecycleModificationException;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialLifecycleEvent;
import com.zkdoc.credential.CredentialLifecycleService;
import com.zkdoc.credential.CredentialNotFoundException;
import com.zkdoc.credential.CredentialService;
import com.zkdoc.credential.CredentialStore;
import com.zkdoc.credential.CredentialVerificationResult;
import com.zkdoc.credential.InvalidLifecycleTransitionException;
import com.zkdoc.credential.SignedCredential;
import com.zkdoc.credential.StoredCredential;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for credential issuance, lifecycle management, and authoritative verification.
 * Enforces authenticated issuer authorization, object-level security, and immutable audit logging.
 */
@RestController
@RequestMapping("/api/credentials")
public class CredentialController {

    private final CredentialService credentialService;
    private final CredentialLifecycleService credentialLifecycleService;
    private final CredentialStore credentialStore;
    private final VerificationAuditService verificationAuditService;
    private final ObjectAuthorizationService objectAuthorizationService;
    private final com.zkdoc.api.metrics.ZkdocMetrics zkdocMetrics;

    @Autowired
    public CredentialController(
            CredentialService credentialService,
            @Autowired(required = false) CredentialLifecycleService credentialLifecycleService,
            @Autowired(required = false) CredentialStore credentialStore,
            VerificationAuditService verificationAuditService,
            ObjectAuthorizationService objectAuthorizationService,
            @Autowired(required = false) com.zkdoc.api.metrics.ZkdocMetrics zkdocMetrics) {
        this.credentialService = credentialService;
        this.credentialLifecycleService = credentialLifecycleService;
        this.credentialStore = credentialStore;
        this.verificationAuditService = verificationAuditService;
        this.objectAuthorizationService = objectAuthorizationService;
        this.zkdocMetrics = zkdocMetrics;
    }

    public CredentialController(
            CredentialService credentialService,
            VerificationAuditService verificationAuditService,
            ObjectAuthorizationService objectAuthorizationService) {
        this(credentialService, null, null, verificationAuditService, objectAuthorizationService, null);
    }

    @PostMapping
    public ResponseEntity<IssueCredentialResponse> issue(
            @jakarta.validation.Valid @RequestBody IssueCredentialRequest request,
            Authentication authentication) {
        // Enforce object-level authorization: prevent foreign issuer impersonation
        objectAuthorizationService.checkCredentialIssuance(authentication, request.issuerId());

        long start = System.nanoTime();
        Credential credential = request.toDomain();
        SignedCredential issued = credentialService.issue(credential);
        verificationAuditService.record(authentication, "CREDENTIAL_ISSUED", issued.credential().credentialId(), null, true);
        if (zkdocMetrics != null) {
            zkdocMetrics.recordCredentialIssued(java.time.Duration.ofNanos(System.nanoTime() - start));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new IssueCredentialResponse(issued.credential().credentialId(), HexFormat.of().formatHex(issued.digest())));
    }

    @GetMapping("/{id}/verify")
    public CredentialVerificationResult verify(
            @PathVariable UUID id,
            @RequestParam(required = false) Instant asOf,
            Authentication authentication) {
        try {
            CredentialVerificationResult result;
            if (asOf != null) {
                result = credentialService.verifyHistorical(id, asOf);
                verificationAuditService.record(authentication, "CREDENTIAL_VERIFY_HISTORICAL", id, null, result.verified());
            } else {
                result = credentialService.verify(id);
                verificationAuditService.record(authentication, "CREDENTIAL_VERIFY", id, null, result.verified());
            }
            return result;
        } catch (CredentialNotFoundException exception) {
            verificationAuditService.record(authentication, "CREDENTIAL_VERIFY", id, null, false);
            throw exception;
        }
    }

    @PostMapping("/{id}/suspend")
    public CredentialLifecycleResponse suspend(
            @PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody(required = false) SuspendCredentialRequest request,
            Authentication authentication) {
        objectAuthorizationService.checkCredentialLifecycle(authentication, id);
        if (credentialLifecycleService == null) {
            throw new IllegalStateException("CredentialLifecycleService not configured");
        }

        String actor = authentication != null ? authentication.getName() : "system";
        String role = extractRole(authentication);
        String reason = request != null ? request.reason() : null;

        StoredCredential updated = credentialLifecycleService.suspend(id, actor, role, reason);
        verificationAuditService.record(authentication, "CREDENTIAL_SUSPEND", id, null, true);
        if (zkdocMetrics != null) {
            zkdocMetrics.recordCredentialTransition("SUSPENDED", "SUCCESS");
        }
        return CredentialLifecycleResponse.from(updated, "Credential suspended successfully");
    }

    @PostMapping("/{id}/resume")
    public CredentialLifecycleResponse resume(
            @PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody(required = false) ResumeCredentialRequest request,
            Authentication authentication) {
        objectAuthorizationService.checkCredentialLifecycle(authentication, id);
        if (credentialLifecycleService == null) {
            throw new IllegalStateException("CredentialLifecycleService not configured");
        }

        String actor = authentication != null ? authentication.getName() : "system";
        String role = extractRole(authentication);
        String reason = request != null ? request.reason() : null;

        StoredCredential updated = credentialLifecycleService.resume(id, actor, role, reason);
        verificationAuditService.record(authentication, "CREDENTIAL_RESUME", id, null, true);
        if (zkdocMetrics != null) {
            zkdocMetrics.recordCredentialTransition("ACTIVE", "SUCCESS");
        }
        return CredentialLifecycleResponse.from(updated, "Credential resumed to ACTIVE successfully");
    }

    @PostMapping("/{id}/revoke")
    public CredentialLifecycleResponse revoke(
            @PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody(required = false) RevokeCredentialRequest request,
            Authentication authentication) {
        objectAuthorizationService.checkCredentialLifecycle(authentication, id);
        if (credentialLifecycleService == null) {
            throw new IllegalStateException("CredentialLifecycleService not configured");
        }

        String actor = authentication != null ? authentication.getName() : "system";
        String role = extractRole(authentication);
        String reason = request != null ? request.reason() : null;

        StoredCredential updated = credentialLifecycleService.revoke(id, actor, role, reason);
        verificationAuditService.record(authentication, "CREDENTIAL_REVOKE", id, null, true);
        if (zkdocMetrics != null) {
            zkdocMetrics.recordCredentialTransition("REVOKED", "SUCCESS");
        }
        return CredentialLifecycleResponse.from(updated, "Credential revoked successfully");
    }

    @GetMapping("/{id}/lifecycle")
    public CredentialLifecycleResponse getLifecycleStatus(
            @PathVariable UUID id,
            Authentication authentication) {
        objectAuthorizationService.checkCredentialLifecycleAccess(authentication, id);
        if (credentialStore == null) {
            throw new IllegalStateException("CredentialStore not configured");
        }
        StoredCredential stored = credentialStore.findById(id)
                .orElseThrow(() -> new CredentialNotFoundException(id));
        return CredentialLifecycleResponse.from(stored, "Current status: " + stored.status());
    }

    @GetMapping("/{id}/lifecycle/history")
    public List<CredentialLifecycleEvent> getLifecycleHistory(
            @PathVariable UUID id,
            Authentication authentication) {
        objectAuthorizationService.checkCredentialLifecycleAccess(authentication, id);
        if (credentialLifecycleService == null) {
            throw new IllegalStateException("CredentialLifecycleService not configured");
        }
        return credentialLifecycleService.getHistory(id);
    }

    @ExceptionHandler(InvalidLifecycleTransitionException.class)
    public ResponseEntity<String> handleInvalidTransition(InvalidLifecycleTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("{\"error\":\"InvalidLifecycleTransition\",\"message\":\"" + ex.getMessage() + "\"}");
    }

    @ExceptionHandler(ConcurrentLifecycleModificationException.class)
    public ResponseEntity<String> handleConcurrentConflict(ConcurrentLifecycleModificationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("{\"error\":\"ConcurrentLifecycleConflict\",\"message\":\"" + ex.getMessage() + "\"}");
    }

    private String extractRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null || authentication.getAuthorities().isEmpty()) {
            return "ISSUER";
        }
        return authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
    }

    public record IssueCredentialResponse(UUID credentialId, String digest) {
    }
}
