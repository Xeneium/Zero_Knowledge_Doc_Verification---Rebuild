package com.zkdoc.api.audit;

import java.util.Objects;
import java.util.UUID;

/**
 * Request payload for submitting an audit event.
 */
public record AuditEventRequest(
        AuditEventType eventType,
        String actorSubject,
        ActorType actorType,
        TargetType targetType,
        String targetId,
        String action,
        AuditOutcome outcome,
        String reasonCode,
        String correlationId,
        String clientIp,
        String metadataJson
) {
    public AuditEventRequest {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(actorSubject, "actorSubject must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
    }

    public static Builder builder(AuditEventType eventType) {
        return new Builder(eventType);
    }

    public static class Builder {
        private final AuditEventType eventType;
        private String actorSubject = "system";
        private ActorType actorType = ActorType.SYSTEM;
        private TargetType targetType;
        private String targetId;
        private String action;
        private AuditOutcome outcome = AuditOutcome.SUCCESS;
        private String reasonCode;
        private String correlationId;
        private String clientIp;
        private String metadataJson;

        public Builder(AuditEventType eventType) {
            this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
            this.action = eventType.name();
        }

        public Builder actor(String actorSubject, ActorType actorType) {
            this.actorSubject = actorSubject != null ? actorSubject : "system";
            this.actorType = actorType != null ? actorType : ActorType.SYSTEM;
            return this;
        }

        public Builder target(TargetType targetType, String targetId) {
            this.targetType = targetType;
            this.targetId = targetId;
            return this;
        }

        public Builder target(TargetType targetType, UUID targetId) {
            this.targetType = targetType;
            this.targetId = targetId != null ? targetId.toString() : null;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder outcome(AuditOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder reasonCode(String reasonCode) {
            this.reasonCode = reasonCode;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        public Builder metadataJson(String metadataJson) {
            this.metadataJson = metadataJson;
            return this;
        }

        public AuditEventRequest build() {
            return new AuditEventRequest(
                    eventType,
                    actorSubject,
                    actorType,
                    targetType,
                    targetId,
                    action != null ? action : eventType.name(),
                    outcome,
                    reasonCode,
                    correlationId,
                    clientIp,
                    metadataJson
            );
        }
    }
}
