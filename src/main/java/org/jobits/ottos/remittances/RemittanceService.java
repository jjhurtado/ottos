package org.jobits.ottos.remittances;

import org.jobits.ottos.remittances.domain.RemittanceEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemittanceService {

    /**
     * Registers a remittance, already paid: the sender pays before it is registered (in a pickup the courier
     * collects the amount). Repeating a request with the same idempotency key returns the first remittance, also
     * when both requests run at the same time.
     */
    RemittanceView register(NewRemittance request, Viewer viewer);

    /** Assigns (or reassigns) a courier through the first allowed transition to a status that requires one. */
    RemittanceView assign(UUID id, UUID courierId, String note, Viewer viewer);

    /** Marks it delivered (or collected, for pickups) through the allowed transition to a final status. */
    RemittanceView complete(UUID id, String note, Viewer viewer);

    /** Like {@link #complete} but the beneficiary must give the PIN the sender received. */
    RemittanceView completeWithPin(UUID id, String pin, String note, Viewer viewer);

    /**
     * Marks it delivered (or collected) by the business, without a courier, from any open status: an assigned courier
     * is taken off and the business cash hands over or receives the money (docs/contracts/complete-from-business.md).
     */
    RemittanceView completeFromBusiness(UUID id, String note, Viewer viewer);

    /** Moves to any status allowed by remittance_transitions, e.g. a custom status added in the database. */
    RemittanceView transition(UUID id, String toStatus, UUID courierId, String note, Viewer viewer);

    /**
     * Reports why a delivery or pickup could not be done. The remittance keeps it as its open incident until someone
     * acts on it (reassign, postpone, complete or any status change). OTHER needs a note.
     */
    RemittanceView reportIncident(UUID id, IncidentReason reason, String note, Viewer viewer);

    /** Sales figures of the remittances registered in [from, to); "completed" means completed before to. */
    SalesSummary salesSummary(Instant from, Instant to);

    /**
     * The remittances registered in [from, to), one line each, voided ones included and marked, without personal data
     * (docs/contracts/monthly-report.md, section "Detalle"). The lines that are not voided add up to
     * {@link #salesSummary}.
     */
    SalesDetail salesDetail(Instant from, Instant to);

    /**
     * Voids a remittance, whatever its status: it stays for the record (who, when, why) but stops counting in
     * reports, statistics, work lists, the completed feed and the mobile sync, and the cash it moved is reversed.
     * A voided remittance cannot be changed again (REMITTANCE_ALREADY_VOIDED / REMITTANCE_VOIDED).
     */
    RemittanceView voidRemittance(UUID id, String reason, Viewer viewer);

    /** Clears the pending review of a remittance, leaving the note in its history. */
    RemittanceView resolveReview(UUID id, String note, Viewer viewer);

    /** Moves the expected date later. Allowed for any open remittance; recorded with its reason. */
    RemittanceView postpone(UUID id, LocalDate newDate, String reason, Viewer viewer);

    RemittanceView get(UUID id, Viewer viewer);

    /** The statuses and transitions configured in the database. */
    WorkflowView workflow();

    RemittanceView getByCode(String code, Viewer viewer);

    List<EventView> history(UUID id, Viewer viewer);

    /** Open remittances assigned to the viewer, soonest expected date first. */
    List<RemittanceView> assignedTo(Viewer viewer);

    /** Couriers (see StaffDirectoryService#couriers), with how many open remittances each holds. */
    List<CourierView> couriers();

    PageView search(RemittanceFilter filter, int page, int size, Viewer viewer);

    /**
     * Remittances whose completion the server recorded after since, oldest first, for Sales' local notifications.
     * Without since, none: the answer only sets where the next call starts. The next call passes serverTime back.
     */
    CompletedFeed completedSince(Instant since, Viewer viewer);

    /**
     * rate and fee: entered by hand for a pickup (null uses the configured ones); not allowed for deliveries.
     * onTheHouse: a delivery free of fee, with permission remittances:on-the-house and a mandatory onTheHouseReason
     * (docs/contracts/on-the-house.md); the reason is ignored when onTheHouse is false.
     */
    record NewRemittance(RemittanceType type, UUID customerId, UUID beneficiaryId, BigDecimal amount,
                         String targetCurrency, BigDecimal rate, BigDecimal fee, String notes, String idempotencyKey,
                         boolean onTheHouse, String onTheHouseReason) {
    }

    record CourierView(UUID id, String name, String email, long openRemittances) {
    }

    /** serverTime: the since of the next call. hasMore: more completions are waiting; call again right away. */
    record CompletedFeed(Instant serverTime, boolean hasMore, List<RemittanceView> remittances) {
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
