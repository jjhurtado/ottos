package org.jobits.ottos.remittances.application;

import org.jobits.ottos.remittances.IncidentReason;
import org.jobits.ottos.remittances.RemittanceType;
import org.jobits.ottos.remittances.ReviewReason;
import org.jobits.ottos.remittances.domain.RemittanceEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemittanceService {

    /**
     * Registers a remittance, already paid: the sender pays before it is registered (pickups are paid through
     * the courier). Repeating a request with the same idempotency key returns the first remittance; when two such
     * requests run at the same time, the second fails with DataIntegrityViolationException and the caller looks the
     * first one up with {@link #findByIdempotencyKey}.
     */
    RemittanceView register(NewRemittance request, Viewer viewer);

    /** Assigns (or reassigns) a courier through the first allowed transition to a status that requires one. */
    RemittanceView assign(UUID id, UUID courierId, String note, Viewer viewer);

    /** Marks it delivered (or collected, for pickups) through the allowed transition to a final status. */
    RemittanceView complete(UUID id, String note, Viewer viewer);

    /** Like {@link #complete} but the beneficiary must give the PIN the sender received. */
    RemittanceView completeWithPin(UUID id, String pin, String note, Viewer viewer);

    /** Moves to any status allowed by remittance_transitions, e.g. a custom status added in the database. */
    RemittanceView transition(UUID id, String toStatus, UUID courierId, String note, Viewer viewer);

    /**
     * Reports why a delivery or pickup could not be done. The remittance keeps it as its open incident until someone
     * acts on it (reassign, postpone, complete or any status change). OTHER needs a note.
     */
    RemittanceView reportIncident(UUID id, IncidentReason reason, String note, Viewer viewer);

    /**
     * Applies one action of the mobile app's offline queue, at the moment it happened (occurredAt), in its own
     * transaction. Sending an action again returns ALREADY_APPLIED. Rejections are thrown as ApiException with the
     * same codes as the online endpoints, plus INVALID_OCCURRED_AT and ACTION_ID_CONFLICT. See
     * docs/contracts/offline-sync.md for the conflict and review rules.
     */
    SyncOutcome applySynced(SyncAction action, Viewer viewer);

    /**
     * Open remittances assigned to the viewer that changed after since, and the ones to remove from the phone; all of
     * them (full) without since or when since is more than 7 days old. Empty without remittances:read-assigned.
     */
    AssignedChanges assignedChangesSince(Instant since, Viewer viewer);

    /** Clears the pending review of a remittance, leaving the note in its history. */
    RemittanceView resolveReview(UUID id, String note, Viewer viewer);

    /** Moves the expected date later. Allowed for any open remittance; recorded with its reason. */
    RemittanceView postpone(UUID id, LocalDate newDate, String reason, Viewer viewer);

    RemittanceView get(UUID id, Viewer viewer);

    /** The statuses and transitions configured in the database. */
    Workflow.WorkflowView workflow();

    Optional<RemittanceView> findByIdempotencyKey(String idempotencyKey, Viewer viewer);

    RemittanceView getByCode(String code, Viewer viewer);

    List<EventView> history(UUID id, Viewer viewer);

    /** Open remittances assigned to the viewer, soonest expected date first. */
    List<RemittanceView> assignedTo(Viewer viewer);

    /** Staff who can be assigned remittances (active, with remittances:deliver), with how many open ones they hold. */
    List<CourierView> couriers();

    PageView search(RemittanceFilter filter, int page, int size, Viewer viewer);

    record NewRemittance(RemittanceType type, UUID customerId, UUID beneficiaryId, BigDecimal amount,
                         String targetCurrency, String notes, String idempotencyKey) {
    }

    record CourierView(UUID id, String name, String email, long openRemittances) {
    }

    record AssignedChanges(boolean full, List<RemittanceView> remittances, List<UUID> removedIds) {
    }

    record PageView(List<RemittanceView> items, int page, int size, long totalItems) {
    }

    record EventView(UUID id, String type, String fromStatus, String toStatus, UUID courierId,
                     LocalDate previousDate, LocalDate newDate, IncidentReason incidentReason,
                     ReviewReason reviewReason, String note, UUID actorId, Instant occurredAt) {

        static EventView of(RemittanceEvent e) {
            return new EventView(e.getId(), e.getType().name(), e.getFromStatus(), e.getToStatus(), e.getCourierId(),
                    e.getPreviousDate(), e.getNewDate(), e.getIncidentReason(), e.getReviewReason(), e.getNote(),
                    e.getActorId(), e.getOccurredAt());
        }
    }
}
