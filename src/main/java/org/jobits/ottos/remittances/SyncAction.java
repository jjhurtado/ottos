package org.jobits.ottos.remittances;


import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One action of the mobile app's offline queue. Only the fields of its type are used:
 * DELIVER (note), DELIVER_WITH_PIN (pin, strictPin, note), POSTPONE (newDate, reason),
 * REPORT_INCIDENT (incidentReason, note).
 *
 * @param id         generated on the phone; the idempotency key
 * @param occurredAt when it happened, by the phone's clock
 * @param strictPin  true when sent right away with network: a wrong PIN is rejected as online
 */
public record SyncAction(UUID id, SyncActionType type, UUID remittanceId, Instant occurredAt, String deviceId,
                         String note, String pin, boolean strictPin, LocalDate newDate, String reason,
                         IncidentReason incidentReason) {
}
