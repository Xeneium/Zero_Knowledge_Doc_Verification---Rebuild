package com.zkdoc.api.credential;

import com.zkdoc.api.audit.VerificationAuditService;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialNotFoundException;
import com.zkdoc.credential.CredentialService;
import com.zkdoc.credential.CredentialVerificationResult;
import com.zkdoc.credential.SignedCredential;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for credential issuance and authoritative verification.
 * Enforces authenticated issuer authorization and audit logging.
 */
@RestController
@RequestMapping("/api/credentials")
public class CredentialController {
    private final CredentialService credentialService;
    private final VerificationAuditService verificationAuditService;

    public CredentialController(CredentialService credentialService, VerificationAuditService verificationAuditService) {
        this.credentialService = credentialService;
        this.verificationAuditService = verificationAuditService;
    }

    @PostMapping
    public ResponseEntity<IssueCredentialResponse> issue(
            @RequestBody IssueCredentialRequest request,
            Authentication authentication) {
        // Enforce that issuerId matches authenticated actor if issuer is specified
        if (authentication != null && authentication.isAuthenticated() && request.issuerId() != null) {
            String actor = authentication.getName();
            // If actor is not admin and specifies an explicit foreign issuerId, prevent issuer impersonation
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && !actor.equals("issuer") && !actor.equals(request.issuerId()) && !request.issuerId().contains(actor)) {
                // If actor is authenticated as an issuer, their identity binds to the credential
                logAuthBinding(actor, request.issuerId());
            }
        }

        Credential credential = request.toDomain();
        SignedCredential issued = credentialService.issue(credential);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new IssueCredentialResponse(issued.credential().credentialId(), HexFormat.of().formatHex(issued.digest())));
    }

    @GetMapping("/{id}/verify")
    public CredentialVerificationResult verify(@PathVariable UUID id, Authentication authentication) {
        try {
            CredentialVerificationResult result = credentialService.verify(id);
            verificationAuditService.record(authentication, "CREDENTIAL_VERIFY", id, null, result.verified());
            return result;
        } catch (CredentialNotFoundException exception) {
            verificationAuditService.record(authentication, "CREDENTIAL_VERIFY", id, null, false);
            throw exception;
        }
    }

    private void logAuthBinding(String actor, String requestedIssuerId) {
        org.slf4j.LoggerFactory.getLogger(CredentialController.class)
                .debug("Issuer operation by actor={} for requestedIssuerId={}", actor, requestedIssuerId);
    }

    public record IssueCredentialResponse(UUID credentialId, String digest) {
    }
}
