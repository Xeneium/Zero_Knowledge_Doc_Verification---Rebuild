package com.zkdoc.api.zkp;

import com.zkdoc.zkp.CredentialPredicateVerificationResult;
import com.zkdoc.zkp.CredentialPredicateVerifier;
import com.zkdoc.api.audit.VerificationAuditService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v2/zkp/credential-predicates")
public class CredentialPredicateController {
    private final CredentialPredicateVerifier predicateVerifier;
    private final ObjectMapper objectMapper;
    private final VerificationAuditService verificationAuditService;
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "proofVersion", "credentialId", "attributeName", "threshold", "nonce", "proofBytes");

    public CredentialPredicateController(CredentialPredicateVerifier predicateVerifier, ObjectMapper objectMapper,
            VerificationAuditService verificationAuditService) {
        this.predicateVerifier = predicateVerifier;
        this.objectMapper = objectMapper;
        this.verificationAuditService = verificationAuditService;
    }

    @PostMapping("/verify")
    public CredentialPredicateVerificationResult verify(@RequestBody JsonNode body, Authentication authentication)
            throws JacksonException {
        if (!body.isObject() || containsUnexpectedField(body)) {
            verificationAuditService.record(authentication, "CREDENTIAL_PREDICATE_VERIFY", null, null, false);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported predicate verification field");
        }
        try {
            PredicateVerificationRequest request = objectMapper.treeToValue(body, PredicateVerificationRequest.class);
            CredentialPredicateVerificationResult result = predicateVerifier.verify(
                    request.proofVersion(), request.credentialId(), request.attributeName(), request.threshold(),
                    request.nonce(), request.proofBytes());
            verificationAuditService.record(authentication, "CREDENTIAL_PREDICATE_VERIFY", request.credentialId(), null,
                    result.verified());
            return result;
        } catch (JacksonException exception) {
            verificationAuditService.record(authentication, "CREDENTIAL_PREDICATE_VERIFY", null, null, false);
            throw exception;
        }
    }

    private boolean containsUnexpectedField(JsonNode body) {
        return body.properties().stream().map(entry -> entry.getKey()).anyMatch(name -> !ALLOWED_FIELDS.contains(name));
    }

    /** This request intentionally has no value, blinding factor, or commitment field. */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PredicateVerificationRequest(
            String proofVersion,
            UUID credentialId,
            String attributeName,
            long threshold,
            byte[] nonce,
            byte[] proofBytes) {
    }
}
