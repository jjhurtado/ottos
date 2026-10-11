package org.jobits.ottos.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The audit log: every {@link org.jobits.ottos.AuditEvent} published by any module is stored in the transaction of the
 * change. Entries are never edited nor deleted.
 */
public interface AuditLogService {

    /** Entries matching the filter, newest first. */
    AuditPage search(AuditFilter filter, int page, int size);

    /**
     * Optional filters; null means "any".
     *
     * @param from first day (inclusive, business timezone)
     * @param to   last day (inclusive, business timezone)
     */
    record AuditFilter(String targetType, String targetId, UUID actorId, String action, LocalDate from, LocalDate to) {
    }

    record AuditPage(List<AuditEntryView> items, int page, int size, long totalItems) {
    }

    /** @param actorName name of the actor now; null for the system or an unknown user */
    record AuditEntryView(UUID id, Instant occurredAt, UUID actorId, String actorName, String action,
                          String targetType, String targetId, String targetLabel, Map<String, Object> details) {
    }
}
