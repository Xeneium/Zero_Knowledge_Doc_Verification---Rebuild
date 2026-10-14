package com.zkdoc.api.audit;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative API endpoint for inspecting the security audit trail
 * and verifying cryptographic tamper-evidence.
 * Strictly restricted to users with the ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditAdminController {

    private final SecurityAuditService auditService;

    public AuditAdminController(SecurityAuditService auditService) {
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
    }

    @GetMapping("/events")
    public ResponseEntity<AuditQueryResult> queryEvents(
            @RequestParam(required = false) AuditEventCategory category,
            @RequestParam(required = false) AuditEventType type,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) TargetType targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AuditQueryFilter filter = new AuditQueryFilter(
                category, type, actor, targetType, targetId, correlationId, from, to, page, size);

        List<SecurityAuditEvent> events = auditService.findEvents(filter);
        long total = auditService.countEvents(filter);

        List<SecurityAuditEventResponse> dtos = events.stream()
                .map(SecurityAuditEventResponse::from)
                .toList();

        return ResponseEntity.ok(AuditQueryResult.of(dtos, total, filter.page(), filter.size()));
    }

    @GetMapping("/verify-integrity")
    public ResponseEntity<AuditIntegrityResult> verifyIntegrity(
            @RequestParam(defaultValue = "1") long startSeq,
            @RequestParam(defaultValue = "9223372036854775807") long endSeq) {

        AuditIntegrityResult result = auditService.verifyChainIntegrity(startSeq, endSeq);
        return ResponseEntity.ok(result);
    }
}
