package com.zkdoc.api.demo;

import com.zkdoc.api.audit.AuditEventRequest;
import com.zkdoc.api.audit.AuditEventType;
import com.zkdoc.api.audit.AuditOutcome;
import com.zkdoc.api.audit.SecurityAuditService;
import com.zkdoc.api.audit.TargetType;
import com.zkdoc.api.qr.CapabilityId;
import com.zkdoc.api.qr.CapabilityStatus;
import com.zkdoc.api.qr.QrVerificationPayload;
import com.zkdoc.api.qr.VerificationCapability;
import com.zkdoc.api.qr.VerificationCapabilityStore;
import com.zkdoc.api.qr.VerificationMode;
import com.zkdoc.document.Sha256Hasher;
import com.zkdoc.zkp.AttributeCommitmentResult;
import com.zkdoc.credential.CanonicalizationVersion;
import com.zkdoc.credential.Credential;
import com.zkdoc.credential.CredentialAttribute;
import com.zkdoc.credential.CredentialId;
import com.zkdoc.credential.CredentialRsaPssSigner;
import com.zkdoc.credential.CredentialService;
import com.zkdoc.credential.CredentialStatus;
import com.zkdoc.credential.CredentialStore;
import com.zkdoc.credential.CredentialVersion;
import com.zkdoc.credential.HolderCredentialId;
import com.zkdoc.credential.HolderCredentialService;
import com.zkdoc.credential.HolderCredentialStore;
import com.zkdoc.credential.HolderId;
import com.zkdoc.credential.Issuer;
import com.zkdoc.credential.IssuerId;
import com.zkdoc.credential.IssuerStatus;
import com.zkdoc.credential.IssuerStore;
import com.zkdoc.credential.KeyId;
import com.zkdoc.document.ArtifactId;
import com.zkdoc.document.ArtifactStore;
import com.zkdoc.document.DocumentArtifact;
import com.zkdoc.document.DocumentId;
import com.zkdoc.document.DocumentRecord;
import com.zkdoc.document.DocumentStore;
import com.zkdoc.document.RsaPssSigner;
import com.zkdoc.document.storage.DocumentArtifactStorage;
import com.zkdoc.keys.DevelopmentSigningKeyProvider;
import com.zkdoc.keys.KeyOwnerType;
import com.zkdoc.keys.KeyPurpose;
import com.zkdoc.keys.KeyRegistry;
import com.zkdoc.keys.KeyStatus;
import com.zkdoc.keys.RegisteredKey;
import com.zkdoc.zkp.HolderProvingMaterialStore;
import com.zkdoc.zkp.PedersenCommitmentService;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production-quality Demonstration Data Initializer for the Zero-Knowledge Document Verification Platform.
 * Populates a reproducible, realistic demonstration environment with synthetic entities,
 * pre-registered cryptographic keys, watermarked documents, verifiable credentials,
 * and zero-knowledge Bulletproof proving material.
 */
@Component
@ConditionalOnProperty(name = "zkdoc.demo.seed-enabled", havingValue = "true", matchIfMissing = false)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final KeyRegistry keyRegistry;
    private final DevelopmentSigningKeyProvider signingKeyProvider;
    private final IssuerStore issuerStore;
    private final CredentialStore credentialStore;
    private final CredentialService credentialService;
    private final HolderCredentialStore holderCredentialStore;
    private final HolderCredentialService holderCredentialService;
    private final PedersenCommitmentService pedersenCommitmentService;
    private final HolderProvingMaterialStore provingMaterialStore;
    private final DocumentStore documentStore;
    private final ArtifactStore artifactStore;
    private final DocumentArtifactStorage artifactStorage;
    private final RsaPssSigner rsaPssSigner;
    private final CredentialRsaPssSigner credentialSigner;
    private final Sha256Hasher sha256Hasher;
    private final VerificationCapabilityStore capabilityStore;
    private final SecurityAuditService auditService;
    private final DemoTokenService demoTokenService;
    private final org.springframework.core.env.Environment environment;

    @Autowired
    public DemoDataInitializer(
            KeyRegistry keyRegistry,
            DevelopmentSigningKeyProvider signingKeyProvider,
            @Autowired(required = false) IssuerStore issuerStore,
            CredentialStore credentialStore,
            CredentialService credentialService,
            HolderCredentialStore holderCredentialStore,
            HolderCredentialService holderCredentialService,
            PedersenCommitmentService pedersenCommitmentService,
            HolderProvingMaterialStore provingMaterialStore,
            DocumentStore documentStore,
            @Autowired(required = false) ArtifactStore artifactStore,
            @Autowired(required = false) DocumentArtifactStorage artifactStorage,
            RsaPssSigner rsaPssSigner,
            CredentialRsaPssSigner credentialSigner,
            Sha256Hasher sha256Hasher,
            VerificationCapabilityStore capabilityStore,
            @Autowired(required = false) SecurityAuditService auditService,
            DemoTokenService demoTokenService,
            org.springframework.core.env.Environment environment) {
        this.keyRegistry = keyRegistry;
        this.signingKeyProvider = signingKeyProvider;
        this.issuerStore = issuerStore;
        this.credentialStore = credentialStore;
        this.credentialService = credentialService;
        this.holderCredentialStore = holderCredentialStore;
        this.holderCredentialService = holderCredentialService;
        this.pedersenCommitmentService = pedersenCommitmentService;
        this.provingMaterialStore = provingMaterialStore;
        this.documentStore = documentStore;
        this.artifactStore = artifactStore;
        this.artifactStorage = artifactStorage;
        this.rsaPssSigner = rsaPssSigner;
        this.credentialSigner = credentialSigner;
        this.sha256Hasher = sha256Hasher;
        this.capabilityStore = capabilityStore;
        this.auditService = auditService;
        this.demoTokenService = demoTokenService;
        this.environment = environment;

        if (this.environment != null && this.environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod", "production"))) {
            throw new IllegalStateException("CRITICAL PRODUCTION SECURITY ERROR: zkdoc.demo.seed-enabled is strictly prohibited in production profiles ('prod', 'production')!");
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment != null && environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod", "production"))) {
            throw new IllegalStateException("CRITICAL PRODUCTION SECURITY ERROR: zkdoc.demo.seed-enabled is strictly prohibited in production profiles ('prod', 'production')!");
        }

        log.info("==========================================================================");
        log.info("  ZKDoc Platform: Initializing Synthetic Demo & Evaluation Environment   ");
        log.info("==========================================================================");

        try {
            seedCryptographicKeys();
            seedIssuers();
            seedDocuments();
            seedCredentialsAndProvingMaterial();
            seedCapabilities();
            seedAuditEvents();
            exportTokensToFile();

            log.info("ZKDoc Platform Demo Environment initialized successfully!");
            log.info("Demo Accounts ready:");
            log.info(" - ADMIN:    {} (Full administration & audit logs)", DemoCryptoKeyConstants.ADMIN_SUBJECT);
            log.info(" - ISSUER:   {} ({})", DemoCryptoKeyConstants.ISSUER_SUBJECT, DemoCryptoKeyConstants.ISSUER_NAME);
            log.info(" - HOLDER:   {} (Wallet & Zero-Knowledge Prover)", DemoCryptoKeyConstants.HOLDER_SUBJECT);
            log.info(" - VERIFIER: {} (Auditor & Proof Verifier)", DemoCryptoKeyConstants.VERIFIER_SUBJECT);
            log.info("==========================================================================");
        } catch (Exception e) {
            log.error("Failed to initialize demo data: {}", e.getMessage(), e);
        }
    }

    private void seedCryptographicKeys() {
        KeyPair jwtKeyPair = DemoCryptoKeyConstants.loadDemoJwtKeyPair();

        // 1. JWT Signing Key
        if (keyRegistry.findByKeyId(DemoCryptoKeyConstants.DEMO_JWT_KEY_ID).isEmpty()) {
            RegisteredKey jwtKey = RegisteredKey.createActive(
                    DemoCryptoKeyConstants.DEMO_JWT_KEY_ID,
                    KeyPurpose.JWT_SIGNING,
                    "urn:zkdoc:platform:auth",
                    KeyOwnerType.PLATFORM,
                    jwtKeyPair.getPublic()
            );
            keyRegistry.register(jwtKey);
            log.info("Registered demo JWT key in KeyRegistry: {}", DemoCryptoKeyConstants.DEMO_JWT_KEY_ID);
        }

        // Register in-memory custody for JWT signing
        signingKeyProvider.registerKey(
                DemoCryptoKeyConstants.DEMO_JWT_KEY_ID,
                KeyPurpose.JWT_SIGNING,
                jwtKeyPair.getPrivate(),
                jwtKeyPair.getPublic()
        );

        // 2. Credential Signing Key
        if (keyRegistry.findByKeyId(DemoCryptoKeyConstants.CREDENTIAL_SIGNING_KEY_ID).isEmpty()) {
            RegisteredKey credKey = RegisteredKey.createActive(
                    DemoCryptoKeyConstants.CREDENTIAL_SIGNING_KEY_ID,
                    KeyPurpose.CREDENTIAL_SIGNING,
                    DemoCryptoKeyConstants.ISSUER_SUBJECT,
                    KeyOwnerType.ISSUER,
                    credentialSigner.getPublicKey()
            );
            keyRegistry.register(credKey);
            log.info("Registered demo credential key in KeyRegistry: {}", DemoCryptoKeyConstants.CREDENTIAL_SIGNING_KEY_ID);
        }

        signingKeyProvider.registerKey(
                DemoCryptoKeyConstants.CREDENTIAL_SIGNING_KEY_ID,
                KeyPurpose.CREDENTIAL_SIGNING,
                credentialSigner.getPrivateKey(),
                credentialSigner.getPublicKey()
        );

        // 3. Document Signing Key
        if (keyRegistry.findByKeyId(DemoCryptoKeyConstants.DOCUMENT_SIGNING_KEY_ID).isEmpty()) {
            RegisteredKey docKey = RegisteredKey.createActive(
                    DemoCryptoKeyConstants.DOCUMENT_SIGNING_KEY_ID,
                    KeyPurpose.DOCUMENT_SIGNING,
                    DemoCryptoKeyConstants.ISSUER_SUBJECT,
                    KeyOwnerType.ISSUER,
                    rsaPssSigner.getPublicKey()
            );
            keyRegistry.register(docKey);
            log.info("Registered demo document key in KeyRegistry: {}", DemoCryptoKeyConstants.DOCUMENT_SIGNING_KEY_ID);
        }

        signingKeyProvider.registerKey(
                DemoCryptoKeyConstants.DOCUMENT_SIGNING_KEY_ID,
                KeyPurpose.DOCUMENT_SIGNING,
                rsaPssSigner.getPrivateKey(),
                rsaPssSigner.getPublicKey()
        );
    }

    private void seedIssuers() {
        if (issuerStore == null) {
            return;
        }

        IssuerId acmeId = IssuerId.of(DemoCryptoKeyConstants.ISSUER_SUBJECT);
        if (issuerStore.findById(acmeId).isEmpty()) {
            Issuer acme = Issuer.create(
                    acmeId,
                    DemoCryptoKeyConstants.ISSUER_NAME,
                    KeyId.of(DemoCryptoKeyConstants.CREDENTIAL_SIGNING_KEY_ID)
            );
            issuerStore.save(acme);
            log.info("Seeded authoritative issuer: {}", DemoCryptoKeyConstants.ISSUER_NAME);
        }

        IssuerId apexId = IssuerId.of(DemoCryptoKeyConstants.ISSUER_2_SUBJECT);
        if (issuerStore.findById(apexId).isEmpty()) {
            Issuer apex = Issuer.create(
                    apexId,
                    DemoCryptoKeyConstants.ISSUER_2_NAME,
                    KeyId.of(DemoCryptoKeyConstants.CREDENTIAL_SIGNING_KEY_ID)
            );
            issuerStore.save(apex);
            log.info("Seeded authoritative issuer: {}", DemoCryptoKeyConstants.ISSUER_2_NAME);
        }
    }

    private void seedDocuments() {
        byte[] diplomaBytes = loadPdfFixture("demo-degree-diploma.pdf", "ACME UNIVERSITY DIPLOMA - ALICE CHEN - SYNTHETIC DATA");
        byte[] trainingBytes = loadPdfFixture("demo-training-certificate.pdf", "APEX CYBERSECURITY CERTIFICATE - ALICE CHEN - SYNTHETIC DATA");

        // Document 1: Degree Diploma
        if (documentStore.findById(DemoCryptoKeyConstants.DEMO_DOC_1_ID).isEmpty()) {
            byte[] digest = sha256Hasher.hash(diplomaBytes);
            byte[] signature = rsaPssSigner.sign(digest);

            DocumentRecord docRecord = new DocumentRecord(
                    DemoCryptoKeyConstants.DEMO_DOC_1_ID,
                    diplomaBytes,
                    digest,
                    signature,
                    DemoCryptoKeyConstants.DEMO_DOC_1_TOKEN,
                    true,
                    DemoCryptoKeyConstants.ISSUER_SUBJECT
            );
            documentStore.save(docRecord);

            persistArtifact(DemoCryptoKeyConstants.DEMO_DOC_1_ID, diplomaBytes, "application/pdf", digest);
            log.info("Seeded document 1: {} (Token: {})", DemoCryptoKeyConstants.DEMO_DOC_1_ID, DemoCryptoKeyConstants.DEMO_DOC_1_TOKEN);
        }

        // Document 2: Training Certificate
        if (documentStore.findById(DemoCryptoKeyConstants.DEMO_DOC_2_ID).isEmpty()) {
            byte[] digest = sha256Hasher.hash(trainingBytes);
            byte[] signature = rsaPssSigner.sign(digest);

            DocumentRecord docRecord = new DocumentRecord(
                    DemoCryptoKeyConstants.DEMO_DOC_2_ID,
                    trainingBytes,
                    digest,
                    signature,
                    DemoCryptoKeyConstants.DEMO_DOC_2_TOKEN,
                    true,
                    DemoCryptoKeyConstants.ISSUER_SUBJECT
            );
            documentStore.save(docRecord);

            persistArtifact(DemoCryptoKeyConstants.DEMO_DOC_2_ID, trainingBytes, "application/pdf", digest);
            log.info("Seeded document 2: {} (Token: {})", DemoCryptoKeyConstants.DEMO_DOC_2_ID, DemoCryptoKeyConstants.DEMO_DOC_2_TOKEN);
        }
    }

    private void persistArtifact(UUID documentId, byte[] content, String contentType, byte[] digest) {
        String storageKey = "artifacts/" + documentId + "/artifact.bin";
        if (artifactStorage != null) {
            try {
                artifactStorage.store(storageKey, new ByteArrayInputStream(content), content.length);
            } catch (Exception e) {
                log.warn("Could not write document artifact to storage: {}", e.getMessage());
            }
        }
        if (artifactStore != null) {
            try {
                DocumentArtifact artifact = new DocumentArtifact(
                        ArtifactId.random(),
                        DocumentId.of(documentId),
                        1,
                        contentType,
                        content.length,
                        com.zkdoc.core.crypto.Digest.of(digest),
                        storageKey,
                        com.zkdoc.document.ArtifactStatus.ACTIVE,
                        Instant.now()
                );
                artifactStore.save(artifact);
            } catch (Exception e) {
                log.warn("Could not save artifact metadata: {}", e.getMessage());
            }
        }
    }

    private void seedCredentialsAndProvingMaterial() {
        Instant now = Instant.now();
        Instant pastIssued = now.minusSeconds(86400 * 90);
        Instant futureExpiry = now.plusSeconds(86400 * 365 * 4);

        // ---------------------------------------------------------------------
        // Credential 1: Bachelor of Science in Computer Science (Active)
        // ---------------------------------------------------------------------
        if (credentialStore.findById(DemoCryptoKeyConstants.DEMO_CRED_1_ID).isEmpty()) {
            List<CredentialAttribute> attributes1 = List.of(
                    new CredentialAttribute("student_name", "Alice Chen"),
                    new CredentialAttribute("degree", "Bachelor of Science in Computer Science"),
                    new CredentialAttribute("gpa", 380), // Scaled 3.80 * 100
                    new CredentialAttribute("graduation_year", 2024),
                    new CredentialAttribute("age", 24)
            );

            Credential cred1 = new Credential(
                    CredentialId.of(DemoCryptoKeyConstants.DEMO_CRED_1_ID),
                    "urn:zkdoc:schema:degree:v1",
                    IssuerId.of(DemoCryptoKeyConstants.ISSUER_SUBJECT),
                    KeyId.of(DemoCryptoKeyConstants.CREDENTIAL_SIGNING_KEY_ID),
                    CredentialVersion.V1,
                    pastIssued,
                    futureExpiry,
                    CredentialStatus.ACTIVE,
                    attributes1,
                    CanonicalizationVersion.V1
            );
            credentialService.issue(cred1);

            // Generate cryptographic commitments and secret opening witness
            commitAndSaveSecret(DemoCryptoKeyConstants.DEMO_CRED_1_ID, "age", 24);
            commitAndSaveSecret(DemoCryptoKeyConstants.DEMO_CRED_1_ID, "gpa", 380);
            commitAndSaveSecret(DemoCryptoKeyConstants.DEMO_CRED_1_ID, "graduation_year", 2024);

            provisionToHolder(DemoCryptoKeyConstants.DEMO_CRED_1_ID);
            log.info("Issued Credential 1: Bachelor Degree (Alice Chen)");
        }

        // ---------------------------------------------------------------------
        // Credential 2: Certified Cloud Security Architect (Active)
        // ---------------------------------------------------------------------
        if (credentialStore.findById(DemoCryptoKeyConstants.DEMO_CRED_2_ID).isEmpty()) {
            List<CredentialAttribute> attributes2 = List.of(
                    new CredentialAttribute("holder_name", "Alice Chen"),
                    new CredentialAttribute("certification", "Certified Cloud Security Architect"),
                    new CredentialAttribute("score", 94),
                    new CredentialAttribute("experience_years", 5)
            );

            Credential cred2 = new Credential(
                    CredentialId.of(DemoCryptoKeyConstants.DEMO_CRED_2_ID),
                    "urn:zkdoc:schema:certification:v1",
                    IssuerId.of(DemoCryptoKeyConstants.ISSUER_2_SUBJECT),
                    KeyId.of(DemoCryptoKeyConstants.CREDENTIAL_SIGNING_KEY_ID),
                    CredentialVersion.V1,
                    pastIssued,
                    futureExpiry,
                    CredentialStatus.ACTIVE,
                    attributes2,
                    CanonicalizationVersion.V1
            );
            credentialService.issue(cred2);

            commitAndSaveSecret(DemoCryptoKeyConstants.DEMO_CRED_2_ID, "score", 94);
            commitAndSaveSecret(DemoCryptoKeyConstants.DEMO_CRED_2_ID, "experience_years", 5);

            provisionToHolder(DemoCryptoKeyConstants.DEMO_CRED_2_ID);
            log.info("Issued Credential 2: Cloud Architect Certification (Alice Chen)");
        }

        // ---------------------------------------------------------------------
        // Credential 3: Special Research Laboratory Permit (Suspended)
        // ---------------------------------------------------------------------
        if (credentialStore.findById(DemoCryptoKeyConstants.DEMO_CRED_3_ID).isEmpty()) {
            List<CredentialAttribute> attributes3 = List.of(
                    new CredentialAttribute("holder_name", "Alice Chen"),
                    new CredentialAttribute("permit_type", "High-Security Research Lab Access"),
                    new CredentialAttribute("clearance_level", 3),
                    new CredentialAttribute("review_status", "TEMPORARY_ADMINISTRATIVE_SUSPENSION")
            );

            Credential cred3 = new Credential(
                    CredentialId.of(DemoCryptoKeyConstants.DEMO_CRED_3_ID),
                    "urn:zkdoc:schema:permit:v1",
                    IssuerId.of(DemoCryptoKeyConstants.ISSUER_SUBJECT),
                    KeyId.of(DemoCryptoKeyConstants.CREDENTIAL_SIGNING_KEY_ID),
                    CredentialVersion.V1,
                    pastIssued,
                    futureExpiry,
                    CredentialStatus.SUSPENDED,
                    attributes3,
                    CanonicalizationVersion.V1
            );
            credentialService.issue(cred3);

            provisionToHolder(DemoCryptoKeyConstants.DEMO_CRED_3_ID);
            log.info("Issued Credential 3: Suspended Research Permit");
        }

        // ---------------------------------------------------------------------
        // Credential 4: Equipment & Facility Pass (Revoked)
        // ---------------------------------------------------------------------
        if (credentialStore.findById(DemoCryptoKeyConstants.DEMO_CRED_4_ID).isEmpty()) {
            List<CredentialAttribute> attributes4 = List.of(
                    new CredentialAttribute("holder_name", "Alice Chen"),
                    new CredentialAttribute("facility_pass", "Campus Hardware Lab 4B"),
                    new CredentialAttribute("revocation_reason", "Superceded by Graduate Access Badge")
            );

            Credential cred4 = new Credential(
                    CredentialId.of(DemoCryptoKeyConstants.DEMO_CRED_4_ID),
                    "urn:zkdoc:schema:facility:v1",
                    IssuerId.of(DemoCryptoKeyConstants.ISSUER_SUBJECT),
                    KeyId.of(DemoCryptoKeyConstants.CREDENTIAL_SIGNING_KEY_ID),
                    CredentialVersion.V1,
                    pastIssued,
                    now.minusSeconds(86400 * 5),
                    CredentialStatus.REVOKED,
                    attributes4,
                    CanonicalizationVersion.V1
            );
            credentialService.issue(cred4);

            provisionToHolder(DemoCryptoKeyConstants.DEMO_CRED_4_ID);
            log.info("Issued Credential 4: Revoked Facility Pass");
        }
    }

    private void commitAndSaveSecret(UUID credentialId, String attributeName, long integerValue) {
        try {
            AttributeCommitmentResult result = pedersenCommitmentService.commitWithOpening(
                    credentialId,
                    attributeName,
                    integerValue,
                    DemoCryptoKeyConstants.ISSUER_SUBJECT
            );
            provingMaterialStore.save(DemoCryptoKeyConstants.HOLDER_SUBJECT, result.openingSecret());
            provingMaterialStore.save("holder-alice", result.openingSecret());
        } catch (Exception e) {
            log.warn("Could not commit attribute {} for credential {}: {}", attributeName, credentialId, e.getMessage());
        }
    }

    private void provisionToHolder(UUID credentialId) {
        try {
            holderCredentialService.provision(
                    HolderId.of(DemoCryptoKeyConstants.HOLDER_SUBJECT),
                    CredentialId.of(credentialId)
            );
        } catch (Exception e) {
            log.debug("Holder already provisioned for credential {}: {}", credentialId, e.getMessage());
        }
        try {
            holderCredentialService.provision(
                    HolderId.of("holder-alice"),
                    CredentialId.of(credentialId)
            );
        } catch (Exception e) {
            log.debug("holder-alice alias already provisioned: {}", e.getMessage());
        }
    }

    private void seedCapabilities() {
        Instant now = Instant.now();
        Instant futureExpiry = now.plusSeconds(86400 * 365); // 1 year

        // Document capability
        if (capabilityStore.findByToken(DemoCryptoKeyConstants.DEMO_DOC_1_TOKEN).isEmpty()) {
            VerificationCapability capDoc = new VerificationCapability(
                    CapabilityId.random(),
                    DemoCryptoKeyConstants.DEMO_DOC_1_TOKEN,
                    null,
                    DemoCryptoKeyConstants.DEMO_DOC_1_ID,
                    VerificationMode.MODE_A_PRESENTED_ARTIFACT,
                    QrVerificationPayload.PROTOCOL_VERSION_V1,
                    CapabilityStatus.ACTIVE,
                    DemoCryptoKeyConstants.ISSUER_SUBJECT,
                    now,
                    futureExpiry,
                    null,
                    null
            );
            capabilityStore.save(capDoc);
            log.info("Seeded QR capability for document 1: {}", DemoCryptoKeyConstants.DEMO_DOC_1_TOKEN);
        }

        // Credential status capability
        if (capabilityStore.findByToken(DemoCryptoKeyConstants.DEMO_CRED_1_TOKEN).isEmpty()) {
            VerificationCapability capCred = new VerificationCapability(
                    CapabilityId.random(),
                    DemoCryptoKeyConstants.DEMO_CRED_1_TOKEN,
                    DemoCryptoKeyConstants.DEMO_CRED_1_ID,
                    null,
                    VerificationMode.MODE_B_REGISTRY_CAPABILITY,
                    QrVerificationPayload.PROTOCOL_VERSION_V1,
                    CapabilityStatus.ACTIVE,
                    DemoCryptoKeyConstants.ISSUER_SUBJECT,
                    now,
                    futureExpiry,
                    null,
                    null
            );
            capabilityStore.save(capCred);
            log.info("Seeded QR capability for credential 1: {}", DemoCryptoKeyConstants.DEMO_CRED_1_TOKEN);
        }
    }

    private void seedAuditEvents() {
        if (auditService == null) {
            return;
        }

        auditService.record(AuditEventRequest.builder(AuditEventType.ADMIN_SECURITY_OPERATION)
                .actor("system", com.zkdoc.api.audit.ActorType.SYSTEM)
                .target(TargetType.SYSTEM, "zkdoc-platform-api")
                .action("INITIALIZE_PLATFORM")
                .outcome(AuditOutcome.SUCCESS)
                .reasonCode("PLATFORM_READY")
                .build());

        auditService.record(AuditEventRequest.builder(AuditEventType.KEY_REGISTERED)
                .actor(DemoCryptoKeyConstants.ADMIN_SUBJECT, com.zkdoc.api.audit.ActorType.USER)
                .target(TargetType.KEY, DemoCryptoKeyConstants.DEMO_JWT_KEY_ID)
                .action("REGISTER_KEY")
                .outcome(AuditOutcome.SUCCESS)
                .reasonCode("KEY_ACTIVE")
                .build());

        auditService.record(AuditEventRequest.builder(AuditEventType.DOCUMENT_UPLOADED)
                .actor(DemoCryptoKeyConstants.ISSUER_SUBJECT, com.zkdoc.api.audit.ActorType.USER)
                .target(TargetType.DOCUMENT, DemoCryptoKeyConstants.DEMO_DOC_1_ID.toString())
                .action("UPLOAD_DOCUMENT")
                .outcome(AuditOutcome.SUCCESS)
                .reasonCode("INTEGRITY_VERIFIED")
                .build());

        auditService.record(AuditEventRequest.builder(AuditEventType.CREDENTIAL_ISSUED)
                .actor(DemoCryptoKeyConstants.ISSUER_SUBJECT, com.zkdoc.api.audit.ActorType.USER)
                .target(TargetType.CREDENTIAL, DemoCryptoKeyConstants.DEMO_CRED_1_ID.toString())
                .action("ISSUE_CREDENTIAL")
                .outcome(AuditOutcome.SUCCESS)
                .reasonCode("RSASSA_PSS_SIGNED")
                .build());

        log.info("Recorded initial tamper-evident audit ledger events");
    }

    private void exportTokensToFile() {
        try {
            Path demoDir = Paths.get("demo");
            if (!Files.exists(demoDir)) {
                Files.createDirectories(demoDir);
            }

            var accounts = demoTokenService.getAllDemoAccounts();
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"notice\": \"SYNTHETIC DEMO TOKENS - Generated by ZKDoc Platform DemoDataInitializer\",\n");
            sb.append("  \"issuer\": \"").append(DemoCryptoKeyConstants.DEMO_JWT_ISSUER).append("\",\n");
            sb.append("  \"audience\": \"").append(DemoCryptoKeyConstants.DEMO_JWT_AUDIENCE).append("\",\n");
            sb.append("  \"algorithm\": \"RS256\",\n");
            sb.append("  \"keyId\": \"").append(DemoCryptoKeyConstants.DEMO_JWT_KEY_ID).append("\",\n");
            sb.append("  \"accounts\": [\n");

            for (int i = 0; i < accounts.size(); i++) {
                var acc = accounts.get(i);
                sb.append("    {\n");
                sb.append("      \"id\": \"").append(acc.id()).append("\",\n");
                sb.append("      \"name\": \"").append(acc.name()).append("\",\n");
                sb.append("      \"role\": \"").append(acc.role()).append("\",\n");
                sb.append("      \"subject\": \"").append(acc.subject()).append("\",\n");
                sb.append("      \"token\": \"").append(acc.token()).append("\"\n");
                sb.append("    }").append(i < accounts.size() - 1 ? "," : "").append("\n");
            }
            sb.append("  ]\n");
            sb.append("}\n");

            Files.writeString(demoDir.resolve("demo-tokens.json"), sb.toString());
            log.info("Exported demo tokens to demo/demo-tokens.json");
        } catch (Exception e) {
            log.warn("Failed to export demo tokens to file: {}", e.getMessage());
        }
    }

    private byte[] loadPdfFixture(String filename, String fallbackText) {
        try {
            Path fixturePath = Paths.get("demo", "fixtures", filename);
            if (Files.exists(fixturePath)) {
                return Files.readAllBytes(fixturePath);
            }
        } catch (Exception ignored) {
        }
        return ("%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" + fallbackText + "\n%%EOF\n").getBytes();
    }
}
