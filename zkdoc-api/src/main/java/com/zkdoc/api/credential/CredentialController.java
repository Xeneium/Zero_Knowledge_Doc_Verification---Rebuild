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
    public ResponseEntity<IssueCredentialResponse> issue(@RequestBody Credential credential) {
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

    public record IssueCredentialResponse(UUID credentialId, String digest) {
    }
}
