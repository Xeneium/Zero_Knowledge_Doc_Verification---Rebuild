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
    private final com.zkdoc.zkp.holder.PredicateProofGenerationService proofGenerationService;

    public HolderCredentialController(
            HolderCredentialService holderCredentialService,
            VerificationAuditService auditService,
            com.zkdoc.zkp.holder.PredicateProofGenerationService proofGenerationService) {
        this.holderCredentialService = holderCredentialService;
        this.auditService = auditService;
        this.proofGenerationService = proofGenerationService;
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

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('HOLDER') or hasRole('ADMIN')")
    public HolderCredentialResponse suspend(@PathVariable UUID id, Authentication authentication) {
        String subject = authentication != null ? authentication.getName() : "anonymous";
        HolderId holderId = HolderId.of(subject);
        HolderCredential hc = holderCredentialService.suspend(HolderCredentialId.of(id), holderId);
        auditService.record(authentication, "HOLDER_CREDENTIAL_SUSPENDED", id, null, true);
        return toResponse(hc);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('HOLDER') or hasRole('ADMIN')")
    public HolderCredentialResponse activate(@PathVariable UUID id, Authentication authentication) {
        String subject = authentication != null ? authentication.getName() : "anonymous";
        HolderId holderId = HolderId.of(subject);
        HolderCredential hc = holderCredentialService.reactivate(HolderCredentialId.of(id), holderId);
        auditService.record(authentication, "HOLDER_CREDENTIAL_ACTIVATED", id, null, true);
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

    @PostMapping("/proofs/generate")
    @PreAuthorize("hasRole('HOLDER') or hasRole('ADMIN')")
    public ResponseEntity<HolderPredicateProofResponse> generateProof(
            @RequestBody GeneratePredicateProofRequest request,
            Authentication authentication) {
        String subject = authentication != null ? authentication.getName() : "anonymous";
        HolderId holderId = HolderId.of(subject);

        if (request.holderCredentialId() == null || request.attributeName() == null || request.attributeName().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "holderCredentialId and attributeName must not be empty");
        }
        if (request.threshold() < 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "threshold must be non-negative");
        }

        byte[] nonceBytes;
        if (request.nonceHex() == null || request.nonceHex().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "nonceHex must not be empty");
        }
        try {
            nonceBytes = HexFormat.of().parseHex(request.nonceHex());
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "nonceHex must be valid hex", e);
        }

        com.zkdoc.zkp.engine.PredicateOperator op = com.zkdoc.zkp.engine.PredicateOperator.GREATER_THAN_OR_EQUAL;
        if (request.operator() != null && !request.operator().isBlank() && !">=".equals(request.operator()) && !"GREATER_THAN_OR_EQUAL".equalsIgnoreCase(request.operator())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported predicate operator: " + request.operator());
        }

        com.zkdoc.zkp.holder.HolderPredicateProofRequest proofReq = new com.zkdoc.zkp.holder.HolderPredicateProofRequest(
                HolderCredentialId.of(request.holderCredentialId()),
                request.attributeName(),
                op,
                request.threshold(),
                nonceBytes
        );

        try {
            com.zkdoc.zkp.holder.HolderPredicateProof proof = proofGenerationService.generateProof(holderId, proofReq);
            auditService.record(authentication, "HOLDER_PREDICATE_PROOF_GENERATED",
                    proof.statement().credentialId().value(), null, true);

            HolderPredicateProofResponse resp = new HolderPredicateProofResponse(
                    proof.statement().holderCredentialId().value(),
                    proof.statement().credentialId().value(),
                    HexFormat.of().formatHex(proof.statement().credentialDigest()),
                    proof.statement().predicate().attributeName(),
                    proof.statement().predicate().operator().symbol(),
                    proof.statement().predicate().threshold(),
                    HexFormat.of().formatHex(proof.statement().commitmentPoint()),
                    HexFormat.of().formatHex(proof.statement().attestationSignature()),
                    proof.statement().attestationKeyId(),
                    HexFormat.of().formatHex(proof.statement().transcriptNonce()),
                    proof.proofArtifact().proofVersion(),
                    proof.proofArtifact().bitSize(),
                    HexFormat.of().formatHex(proof.proofArtifact().proofBytes())
            );

            return ResponseEntity.ok(resp);
        } catch (com.zkdoc.credential.HolderCredentialNotFoundException e) {
            auditService.record(authentication, "HOLDER_PREDICATE_PROOF_GENERATE_FAILED",
                    null, null, false);
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            auditService.record(authentication, "HOLDER_PREDICATE_PROOF_GENERATE_FAILED",
                    null, null, false);
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            auditService.record(authentication, "HOLDER_PREDICATE_PROOF_GENERATE_FAILED",
                    null, null, false);
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
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
