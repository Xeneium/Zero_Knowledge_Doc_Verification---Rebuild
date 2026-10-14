package com.zkdoc.api.security;

import com.zkdoc.api.qr.CapabilityId;
import com.zkdoc.api.qr.VerificationCapability;
import com.zkdoc.api.qr.VerificationCapabilityStore;
import com.zkdoc.credential.CanonicalCredentialDecoder;
import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.CredentialStore;
import com.zkdoc.credential.HolderCredential;
import com.zkdoc.credential.HolderCredentialId;
import com.zkdoc.credential.HolderCredentialStore;
import com.zkdoc.credential.HolderId;
import com.zkdoc.credential.StoredCredential;
import com.zkdoc.document.DocumentRecord;
import com.zkdoc.document.DocumentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service enforcing object-level authorization (BOLA / IDOR protection).
 * Decouples authenticated identity and roles from authorization decisions over specific resources:
 * - Documents: only document owner or ADMIN can perform sensitive operations (e.g. generate QR).
 * - Credentials: an ISSUER can only issue under their own issuer identity (preventing impersonation).
 * - Capabilities: only the creator or ADMIN can revoke; holders can only mint for their credentials.
 * - Proving material: holder can only access and prove credentials owned by them.
 */
@Service
public class ObjectAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(ObjectAuthorizationService.class);

    private final DocumentStore documentStore;
    private final CredentialStore credentialStore;
    private final HolderCredentialStore holderCredentialStore;
    private final VerificationCapabilityStore capabilityStore;
    private final com.zkdoc.api.audit.SecurityAuditService auditService;

    @org.springframework.beans.factory.annotation.Autowired
    public ObjectAuthorizationService(
            DocumentStore documentStore,
            CredentialStore credentialStore,
            HolderCredentialStore holderCredentialStore,
            VerificationCapabilityStore capabilityStore,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.zkdoc.api.audit.SecurityAuditService auditService) {
        this.documentStore = Objects.requireNonNull(documentStore, "documentStore must not be null");
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore must not be null");
        this.holderCredentialStore = Objects.requireNonNull(holderCredentialStore, "holderCredentialStore must not be null");
        this.capabilityStore = Objects.requireNonNull(capabilityStore, "capabilityStore must not be null");
        this.auditService = auditService;
    }

    public ObjectAuthorizationService(
            DocumentStore documentStore,
            CredentialStore credentialStore,
            HolderCredentialStore holderCredentialStore,
            VerificationCapabilityStore capabilityStore) {
        this(documentStore, credentialStore, holderCredentialStore, capabilityStore, null);
    }

    private void auditAccessDenied(String caller, com.zkdoc.api.audit.TargetType targetType, String targetId, String action, String reasonCode) {
        if (auditService != null) {
            String correlationId = org.slf4j.MDC.get(com.zkdoc.api.logging.MdcCorrelationIdFilter.MDC_KEY);
            auditService.record(com.zkdoc.api.audit.AuditEventRequest.builder(com.zkdoc.api.audit.AuditEventType.OBJECT_ACCESS_DENIED)
                    .actor(caller != null ? caller : "anonymous", com.zkdoc.api.audit.ActorType.USER)
                    .target(targetType, targetId)
                    .action(action)
                    .outcome(com.zkdoc.api.audit.AuditOutcome.DENIED)
                    .reasonCode(reasonCode)
                    .correlationId(correlationId)
                    .build());
        }
    }

    /**
     * Checks if the caller is authorized to access/generate QR for the specified document.
     * Allowed if caller is ADMIN, or caller's principal name matches the document's ownerId.
     */
    public void checkDocumentAccess(Authentication authentication, UUID documentId) {
        if (isAdmin(authentication)) {
            return;
        }

        String caller = getCallerPrincipal(authentication);
        Optional<DocumentRecord> docOpt = documentStore.findById(documentId);
        if (docOpt.isEmpty()) {
            // Let controller or store handle not-found semantics cleanly
            return;
        }

        DocumentRecord doc = docOpt.get();
        if (doc.ownerId() != null && !doc.ownerId().equalsIgnoreCase(caller)) {
            log.warn("Object-level authorization denied: caller '{}' is not owner of document '{}' (owner '{}')",
                    caller, documentId, doc.ownerId());
            auditAccessDenied(caller, com.zkdoc.api.audit.TargetType.DOCUMENT, documentId.toString(), "DOCUMENT_ACCESS", "NOT_OWNER");
            throw new AccessDeniedException("Access denied: You are not authorized to access this document");
        }
    }

    /**
     * Checks if the caller is authorized to issue a credential for the specified requestedIssuerId.
     * Prevents issuer impersonation: caller's principal name must match requestedIssuerId unless ADMIN.
     */
    public void checkCredentialIssuance(Authentication authentication, String requestedIssuerId) {
        if (isAdmin(authentication)) {
            return;
        }

        if (!hasRole(authentication, "ROLE_ISSUER")) {
            String caller = (authentication != null && authentication.getName() != null) ? authentication.getName() : "anonymous";
            log.warn("Object-level authorization denied: caller '{}' does not possess ROLE_ISSUER", caller);
            auditAccessDenied(caller, com.zkdoc.api.audit.TargetType.CREDENTIAL, requestedIssuerId, "CREDENTIAL_ISSUANCE", "NOT_AN_ISSUER");
            throw new AccessDeniedException("Access denied: Caller must possess ROLE_ISSUER to issue credentials");
        }

        String caller = getCallerPrincipal(authentication);
        if (requestedIssuerId != null && !requestedIssuerId.isBlank()) {
            if (!requestedIssuerId.equalsIgnoreCase(caller) && !requestedIssuerId.equalsIgnoreCase("issuer:" + caller)) {
                log.warn("Object-level authorization denied: caller '{}' attempted to issue credential as '{}'",
                        caller, requestedIssuerId);
                auditAccessDenied(caller, com.zkdoc.api.audit.TargetType.CREDENTIAL, requestedIssuerId, "CREDENTIAL_ISSUANCE", "ISSUER_IMPERSONATION");
                throw new AccessDeniedException("Access denied: Caller cannot issue credentials for foreign issuer identity " + requestedIssuerId);
            }
        }
    }

    public void checkCredentialLifecycle(Authentication authentication, UUID credentialId) {
        if (isAdmin(authentication)) {
            return;
        }

        if (!hasRole(authentication, "ROLE_ISSUER")) {
            log.warn("Object-level authorization denied: non-issuer attempted credential lifecycle mutation");
            String caller = getCallerPrincipal(authentication);
            auditAccessDenied(caller, com.zkdoc.api.audit.TargetType.CREDENTIAL, credentialId.toString(), "CREDENTIAL_LIFECYCLE", "NOT_ISSUER_ROLE");
            throw new AccessDeniedException("Access denied: Only the issuing authority or administrator may manage credential lifecycle");
        }

        String caller = getCallerPrincipal(authentication);
        Optional<StoredCredential> credOpt = credentialStore.findById(credentialId);
        if (credOpt.isEmpty()) {
            return;
        }

        StoredCredential cred = credOpt.get();
        String issuerId = cred.issuerId();
        if (issuerId == null || "system".equalsIgnoreCase(issuerId)) {
            try {
                var decoded = CanonicalCredentialDecoder.decode(cred.canonicalPayload());
                issuerId = decoded.issuerId();
            } catch (Exception ignored) {
            }
        }

        if (issuerId != null && !issuerId.equalsIgnoreCase(caller) && !issuerId.equalsIgnoreCase("issuer:" + caller)) {
            log.warn("Object-level authorization denied: issuer '{}' does not own credential '{}' (issuer '{}')",
                    caller, credentialId, issuerId);
            auditAccessDenied(caller, com.zkdoc.api.audit.TargetType.CREDENTIAL, credentialId.toString(), "CREDENTIAL_LIFECYCLE", "NOT_OWNING_ISSUER");
            throw new AccessDeniedException("Access denied: You are not authorized to manage the lifecycle of this credential");
        }
    }

    /**
     * Checks if the caller is authorized to view lifecycle status and history of a credential.
     * Permitted if caller is ADMIN, the issuing authority, or a HOLDER who has been provisioned this credential.
     */
    public void checkCredentialLifecycleAccess(Authentication authentication, UUID credentialId) {
        if (isAdmin(authentication)) {
            return;
        }

        String caller = getCallerPrincipal(authentication);
        boolean isHolder = hasRole(authentication, "ROLE_HOLDER");
        boolean isIssuer = hasRole(authentication, "ROLE_ISSUER");

        if (isHolder) {
            boolean exists = holderCredentialStore.existsByHolderIdAndCredentialId(
                    HolderId.of(caller), CredentialId.of(credentialId));
            if (!exists) {
                log.warn("Object-level authorization denied: holder '{}' does not own credential '{}' for lifecycle access",
                        caller, credentialId);
                auditAccessDenied(caller, com.zkdoc.api.audit.TargetType.CREDENTIAL, credentialId.toString(), "CREDENTIAL_LIFECYCLE_ACCESS", "NOT_PROVISIONED_HOLDER");
                throw new AccessDeniedException("Access denied: Holder is not provisioned this credential");
            }
            return;
        }

        if (isIssuer) {
            Optional<StoredCredential> credOpt = credentialStore.findById(credentialId);
            if (credOpt.isEmpty()) {
                return;
            }
            StoredCredential cred = credOpt.get();
            String issuerId = cred.issuerId();
            if (issuerId == null || "system".equalsIgnoreCase(issuerId)) {
                try {
                    var decoded = CanonicalCredentialDecoder.decode(cred.canonicalPayload());
                    issuerId = decoded.issuerId();
                } catch (Exception ignored) {
                }
            }
            if (issuerId != null && !issuerId.equalsIgnoreCase(caller) && !issuerId.equalsIgnoreCase("issuer:" + caller)) {
                log.warn("Object-level authorization denied: issuer '{}' does not own credential '{}' for lifecycle access (issuer '{}')",
                        caller, credentialId, issuerId);
                auditAccessDenied(caller, com.zkdoc.api.audit.TargetType.CREDENTIAL, credentialId.toString(), "CREDENTIAL_LIFECYCLE_ACCESS", "NOT_OWNING_ISSUER");
                throw new AccessDeniedException("Access denied: Issuer does not own this credential");
            }
            return;
        }

        log.warn("Object-level authorization denied: caller '{}' lacks permitted role for credential lifecycle access", caller);
        auditAccessDenied(caller, com.zkdoc.api.audit.TargetType.CREDENTIAL, credentialId.toString(), "CREDENTIAL_LIFECYCLE_ACCESS", "ROLE_UNAUTHORIZED");
        throw new AccessDeniedException("Access denied: Caller lacks required role to view credential lifecycle");
    }

    /**
     * Checks if the caller is authorized to mint a verification capability for a credential or document.
     * - If HOLDER: must own the credential in holder_credentials.
     * - If ISSUER: must own the document or be the issuer of the credential.
     * - If ADMIN: always allowed.
     */
    public void checkCapabilityCreation(Authentication authentication, UUID credentialId, UUID documentId) {
        if (isAdmin(authentication)) {
            return;
        }

        String caller = getCallerPrincipal(authentication);
        boolean isHolder = hasRole(authentication, "ROLE_HOLDER");
        boolean isIssuer = hasRole(authentication, "ROLE_ISSUER");

        if (credentialId != null) {
            if (isHolder) {
                // Must be provisioned to this holder
                boolean exists = holderCredentialStore.existsByHolderIdAndCredentialId(
                        HolderId.of(caller), CredentialId.of(credentialId));
                if (!exists) {
                    log.warn("Object-level authorization denied: holder '{}' does not own credential '{}'",
                            caller, credentialId);
                    throw new AccessDeniedException("Access denied: Holder does not own the requested credential");
                }
            } else if (isIssuer) {
                // Must be issued by this issuer
                Optional<StoredCredential> credOpt = credentialStore.findById(credentialId);
                if (credOpt.isPresent()) {
                    var decoded = CanonicalCredentialDecoder.decode(credOpt.get().canonicalPayload());
                    if (!decoded.issuerId().equalsIgnoreCase(caller) && !decoded.issuerId().equalsIgnoreCase("issuer:" + caller)) {
                        log.warn("Object-level authorization denied: issuer '{}' does not own issued credential '{}' (issuer '{}')",
                                caller, credentialId, decoded.issuerId());
                        throw new AccessDeniedException("Access denied: Issuer does not own the requested credential");
                    }
                }
            }
        }

        if (documentId != null) {
            Optional<DocumentRecord> docOpt = documentStore.findById(documentId);
            if (docOpt.isPresent()) {
                DocumentRecord doc = docOpt.get();
                if (doc.ownerId() != null && !doc.ownerId().equalsIgnoreCase(caller)) {
                    log.warn("Object-level authorization denied: caller '{}' is not owner of document '{}' for capability",
                            caller, documentId);
                    throw new AccessDeniedException("Access denied: Caller does not own the requested document");
                }
            }
        }
    }

    /**
     * Checks if the caller is authorized to revoke a capability.
     * Allowed only if caller is ADMIN or caller is the creator of the capability.
     */
    public void checkCapabilityRevocation(Authentication authentication, CapabilityId capabilityId) {
        if (isAdmin(authentication)) {
            return;
        }

        String caller = getCallerPrincipal(authentication);
        Optional<VerificationCapability> capOpt = capabilityStore.findById(capabilityId);
        if (capOpt.isEmpty()) {
            return; // Let service or controller handle not-found
        }

        VerificationCapability cap = capOpt.get();
        if (cap.createdBy() != null && !cap.createdBy().equalsIgnoreCase(caller)) {
            log.warn("Object-level authorization denied: caller '{}' is not creator of capability '{}' (createdBy '{}')",
                    caller, capabilityId.value(), cap.createdBy());
            throw new AccessDeniedException("Access denied: Only the creator or an administrator can revoke this capability");
        }
    }

    /**
     * Checks if the caller is authorized to access a holder credential record.
     */
    public void checkHolderCredentialAccess(Authentication authentication, HolderCredentialId holderCredentialId) {
        if (isAdmin(authentication)) {
            return;
        }

        String caller = getCallerPrincipal(authentication);
        Optional<HolderCredential> hcOpt = holderCredentialStore.findById(holderCredentialId);
        if (hcOpt.isEmpty()) {
            return;
        }

        HolderCredential hc = hcOpt.get();
        if (!hc.holderId().value().equalsIgnoreCase(caller)) {
            log.warn("Object-level authorization denied: caller '{}' is not owner of holder credential '{}' (holder '{}')",
                    caller, holderCredentialId.value(), hc.holderId().value());
            throw new AccessDeniedException("Access denied: Holder credential does not belong to authenticated caller");
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN");
    }

    private boolean hasRole(Authentication authentication, String roleName) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase(roleName));
    }

    private String getCallerPrincipal(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authentication required for object-level access");
        }
        return authentication.getName().trim();
    }
}
