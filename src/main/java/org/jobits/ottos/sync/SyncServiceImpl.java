package org.jobits.ottos.sync;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.payments.CashLedgerService;
import org.jobits.ottos.payments.CashLedgerService.NewExchange;
import org.jobits.ottos.payments.CashLedgerService.SyncedExchange;
import org.jobits.ottos.remittances.IncidentReason;
import org.jobits.ottos.remittances.RemittanceService;
import org.jobits.ottos.remittances.RemittanceService.AssignedChanges;
import org.jobits.ottos.remittances.SyncAction;
import org.jobits.ottos.remittances.SyncActionType;
import org.jobits.ottos.remittances.SyncOutcome;
import org.jobits.ottos.remittances.Viewer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Implementation of {@link SyncService}. Not transactional itself: every action commits or fails on its own. */
@Service
class SyncServiceImpl implements SyncService {

    private static final String CASH_READ_OWN = "cash:read-own";
    private static final String EXCHANGE_OWN = "cash:exchange-own";
    private static final int MAX_TEXT = 1000;

    private final RemittanceService remittances;
    private final CashLedgerService cash;

    SyncServiceImpl(RemittanceService remittances, CashLedgerService cash) {
        this.remittances = remittances;
        this.cash = cash;
    }

    @Override
    public SyncState state(Instant since, Viewer viewer) {
        // Taken before reading, so nothing that changes meanwhile is missed by the next download
        Instant serverTime = Instant.now();
        AssignedChanges changes = remittances.assignedChangesSince(since, viewer);
        return new SyncState(serverTime, changes.full(), changes.remittances(), changes.removedIds(),
                viewer.has(CASH_READ_OWN) ? cash.courierStatement(viewer.userId()) : null, remittances.workflow());
    }

    @Override
    public SyncResult apply(String deviceId, List<QueuedAction> actions, Viewer viewer) {
        List<ActionResult> results = new ArrayList<>();
        for (QueuedAction action : actions) {
            results.add(applyOne(deviceId, action, viewer));
        }
        return new SyncResult(Instant.now(), results);
    }

    private ActionResult applyOne(String deviceId, QueuedAction q, Viewer viewer) {
        try {
            if (q.type() == SyncActionKind.CASH_EXCHANGE) {
                return applyExchange(q, viewer);
            }
            SyncOutcome outcome = remittances.applySynced(toSyncAction(deviceId, q), viewer);
            return new ActionResult(q.id(), outcome.status(), outcome.code(), outcome.message(), outcome.remittance(),
                    null, outcome.flaggedForReview());
        } catch (ApiException e) {
            return rejected(q, e.getCode(), e.getReason());
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
            // Someone changed the remittance, or sent the same action, at the same time: retrying resolves it
            return rejected(q, "CONCURRENT_UPDATE", "The remittance was changed at the same time; sync again");
        }
    }

    /** A currency exchange of the courier's own cash; idempotent by the action id, like remittance actions. */
    private ActionResult applyExchange(QueuedAction q, Viewer viewer) {
        if (!viewer.has(EXCHANGE_OWN)) {
            throw ApiException.forbidden("FORBIDDEN", "Exchanging requires " + EXCHANGE_OWN);
        }
        if (q.fromCurrency() == null || q.toCurrency() == null || q.fromAmount() == null || q.rate() == null) {
            throw invalid("fromCurrency, fromAmount, toCurrency and rate are required");
        }
        NewExchange exchange = new NewExchange(q.fromCurrency(), q.fromAmount(), q.toCurrency(), q.rate(),
                optionalText(q.note(), "note"));
        SyncedExchange synced = cash.applySyncedExchange(q.id(), viewer.userId(), exchange, q.occurredAt(),
                viewer.userId());
        SyncOutcome.Status status = synced.alreadyApplied() ? SyncOutcome.Status.ALREADY_APPLIED : SyncOutcome.Status.APPLIED;
        return new ActionResult(q.id(), status, null, null, null, synced.exchange(),
                synced.exchange().review() != null);
    }

    /** Checks the payload of its type; a bad payload rejects only this action, as VALIDATION_FAILED. */
    private static SyncAction toSyncAction(String deviceId, QueuedAction q) {
        if (q.remittanceId() == null) {
            throw invalid("remittanceId is required for " + q.type());
        }
        String note = optionalText(q.note(), "note");
        SyncActionType type = SyncActionType.valueOf(q.type().name());
        return switch (type) {
            case DELIVER -> action(deviceId, q, note, null, false, null, null);
            case DELIVER_WITH_PIN -> {
                if (q.pin() == null || !q.pin().matches("\\d{6}")) {
                    throw invalid("pin must have 6 digits");
                }
                yield action(deviceId, q, note, q.pin(), Boolean.TRUE.equals(q.strictPin()), null, null);
            }
            case POSTPONE -> {
                if (q.newDate() == null) {
                    throw invalid("newDate is required");
                }
                String reason = optionalText(q.reason(), "reason");
                if (reason == null) {
                    throw invalid("reason is required");
                }
                yield new SyncAction(q.id(), type, q.remittanceId(), q.occurredAt(), deviceId, null, null, false,
                        q.newDate(), reason, null);
            }
            case REPORT_INCIDENT -> new SyncAction(q.id(), type, q.remittanceId(), q.occurredAt(), deviceId, note,
                    null, false, null, null, incidentReason(q.reason()));
        };
    }

    private static SyncAction action(String deviceId, QueuedAction q, String note, String pin, boolean strictPin,
                                     LocalDate newDate, String reason) {
        return new SyncAction(q.id(), SyncActionType.valueOf(q.type().name()), q.remittanceId(), q.occurredAt(),
                deviceId, note, pin, strictPin, newDate, reason, null);
    }

    private static IncidentReason incidentReason(String value) {
        try {
            return IncidentReason.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw invalid("reason must be one of NOT_HOME, WRONG_ADDRESS, UNREACHABLE, REFUSED, OTHER");
        }
    }

    private static String optionalText(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > MAX_TEXT) {
            throw invalid(field + " must have at most " + MAX_TEXT + " characters");
        }
        return value;
    }

    private static ApiException invalid(String detail) {
        return ApiException.badRequest("VALIDATION_FAILED", detail);
    }

    private static ActionResult rejected(QueuedAction q, String code, String message) {
        return new ActionResult(q.id(), SyncOutcome.Status.REJECTED, code, message, null, null, false);
    }
}
