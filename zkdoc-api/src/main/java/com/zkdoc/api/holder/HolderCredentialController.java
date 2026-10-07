package com.zkdoc.api.holder;

import com.zkdoc.api.audit.VerificationAuditService;
import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.HolderCredential;
import com.zkdoc.credential.HolderCredentialId;
import com.zkdoc.credential.HolderCredentialService;
import com.zkdoc.credential.HolderId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for holder credentials management and secure provisioning.
 * Strictly enforces holder subject identity authorization, ensuring that Holder A cannot
 * access or manipulate Holder B's credentials (IDOR protection).
 * NEVER returns raw blinding factors or private proving witnesses.
 */
@RestController
@RequestMapping("/api/holder/credentials")
public class HolderCredentialController {

    private final HolderCredentialService holderCredentialService;
    private final VerificationAuditService auditService;

    public HolderCredentialController(
            HolderCredentialService holderCredentialService,
            VerificationAuditService auditService) {
        this.holderCredentialService = holderCredentialService;
        this.auditService = auditService;
    }

    @PostMapping("/provision")
    @PreAuthorize("hasRole('HOLDER') or hasRole('ADMIN')")
    public ResponseEntity<HolderCredentialResponse> provision(
            @RequestBody ProvisionHolderCredentialRequest request,
            Authentication authentication) {
        String subject = authentication != null ? authentication.getName() : "anonymous";
        HolderId holderId = HolderId.of(subject);
        CredentialId credentialId = CredentialId.of(request.credentialId());

        try {
            HolderCredential hc = holderCredentialService.provision(holderId, credentialId);
            auditService.record(authentication, "HOLDER_CREDENTIAL_PROVISIONED", hc.id().value(), null, true);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(hc));
        } catch (Exception e) {
            auditService.record(authentication, "HOLDER_CREDENTIAL_PROVISION_FAILED", request.credentialId(), null, false);
            throw e;
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('HOLDER') or hasRole('ADMIN')")
    public List<HolderCredentialResponse> list(Authentication authentication) {
        String subject = authentication != null ? authentication.getName() : "anonymous";
        HolderId holderId = HolderId.of(subject);
        return holderCredentialService.listByHolder(holderId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HOLDER') or hasRole('ADMIN')")
    public HolderCredentialResponse getById(@PathVariable UUID id, Authentication authentication) {
        String subject = authentication != null ? authentication.getName() : "anonymous";
        HolderId holderId = HolderId.of(subject);
        HolderCredential hc = holderCredentialService.findByIdAndHolder(HolderCredentialId.of(id), holderId);
        auditService.record(authentication, "HOLDER_CREDENTIAL_ACCESSED", id, null, true);
        return toResponse(hc);
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('HOLDER') or hasRole('ADMIN')")
    public HolderCredentialResponse revoke(@PathVariable UUID id, Authentication authentication) {
        String subject = authentication != null ? authentication.getName() : "anonymous";
        HolderId holderId = HolderId.of(subject);
        HolderCredential hc = holderCredentialService.revoke(HolderCredentialId.of(id), holderId);
        auditService.record(authentication, "HOLDER_CREDENTIAL_REVOKED", id, null, true);
        return toResponse(hc);
    }

    private HolderCredentialResponse toResponse(HolderCredential hc) {
        return new HolderCredentialResponse(
                hc.id().value(),
                hc.holderId().value(),
                hc.credentialId().value(),
                HexFormat.of().formatHex(hc.credentialDigest()),
                hc.status().name(),
                hc.version(),
                hc.provisionedAt(),
                hc.updatedAt());
    }
}
