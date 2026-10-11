package org.jobits.ottos;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A change that must leave a trace of who did what and when. Any module publishes it with the Spring event publisher;
 * the reporting module stores it in the audit log within the same transaction, so a change that rolls back leaves no
 * entry. Never put secrets (passwords, hashes, tokens, PINs) in details.
 *
 * @param action      what happened, a stable code such as USER_ROLES_CHANGED
 * @param targetType  what it happened to, such as USER or ROLE
 * @param targetId    the target's id
 * @param targetLabel a readable name of the target at that moment (email, role code…)
 * @param actorId     who did it; null for the system (e.g. the initial administrator)
 * @param details     what changed, e.g. {"before": [...], "after": [...]}
 */
public record AuditEvent(String action, String targetType, String targetId, String targetLabel, UUID actorId,
                         Instant occurredAt, Map<String, Object> details) {

    public AuditEvent {
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }
}
