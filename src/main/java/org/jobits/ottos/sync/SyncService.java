package org.jobits.ottos.sync;

import org.jobits.ottos.payments.application.CashLedgerService.CourierStatement;
import org.jobits.ottos.remittances.application.RemittanceView;
import org.jobits.ottos.remittances.application.SyncActionType;
import org.jobits.ottos.remittances.application.SyncOutcome;
import org.jobits.ottos.remittances.application.Viewer;
import org.jobits.ottos.remittances.application.Workflow.WorkflowView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What the offline-first mobile app downloads and how its queue of offline actions is applied. */
public interface SyncService {

    /** The user's state for working offline: everything (full) without since, or what changed after it. */
    SyncState state(Instant since, Viewer viewer);

    /**
     * Applies the actions in order, each in its own transaction, and returns one result per action in the same order.
     * A rejected action does not stop the next ones.
     */
    SyncResult apply(String deviceId, List<QueuedAction> actions, Viewer viewer);

    /**
     * @param serverTime the "since" of the next download; always the server's clock
     * @param full       true: replace the whole local copy (removedIds is empty)
     * @param cash       null without cash:read-own
     */
    record SyncState(Instant serverTime, boolean full, List<RemittanceView> remittances, List<UUID> removedIds,
                     CourierStatement cash, WorkflowView workflow) {
    }

    /** An action of the queue as the app sends it; its payload is checked here, so a bad one rejects only itself. */
    record QueuedAction(UUID id, SyncActionType type, UUID remittanceId, Instant occurredAt, String note, String pin,
                        Boolean strictPin, LocalDate newDate, String reason) {
    }

    record SyncResult(Instant serverTime, List<ActionResult> results) {
    }

    /** remittance is present when APPLIED or ALREADY_APPLIED; code and message when REJECTED. */
    record ActionResult(UUID id, SyncOutcome.Status status, String code, String message, RemittanceView remittance,
                        boolean flaggedForReview) {
    }
}
