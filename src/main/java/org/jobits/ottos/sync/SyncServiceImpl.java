package org.jobits.ottos.sync;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.payments.application.CashLedgerService;
import org.jobits.ottos.remittances.IncidentReason;
import org.jobits.ottos.remittances.application.RemittanceService;
import org.jobits.ottos.remittances.application.RemittanceService.AssignedChanges;
import org.jobits.ottos.remittances.application.SyncAction;
import org.jobits.ottos.remittances.application.SyncActionType;
import org.jobits.ottos.remittances.application.SyncOutcome;
import org.jobits.ottos.remittances.application.Viewer;
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
            SyncOutcome outcome = remittances.applySynced(toSyncAction(deviceId, q), viewer);
            return new ActionResult(q.id(), outcome.status(), outcome.code(), outcome.message(), outcome.remittance(),
                    outcome.flaggedForReview());
        } catch (ApiException e) {
            return rejected(q, e.getCode(), e.getReason());
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
            // Someone changed the remittance, or sent the same action, at the same time: retrying resolves it
            return rejected(q, "CONCURRENT_UPDATE", "The remittance was changed at the same time; sync again");
        }
    }

    /** Checks the payload of its type; a bad payload rejects only this action, as VALIDATION_FAILED. */
    private static SyncAction toSyncAction(String deviceId, QueuedAction q) {
        String note = optionalText(q.note(), "note");
        return switch (q.type()) {
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
                yield new SyncAction(q.id(), q.type(), q.remittanceId(), q.occurredAt(), deviceId, null, null, false,
                        q.newDate(), reason, null);
            }
            case REPORT_INCIDENT -> new SyncAction(q.id(), q.type(), q.remittanceId(), q.occurredAt(), deviceId, note,
                    null, false, null, null, incidentReason(q.reason()));
        };
    }

    private static SyncAction action(String deviceId, QueuedAction q, String note, String pin, boolean strictPin,
                                     LocalDate newDate, String reason) {
        return new SyncAction(q.id(), q.type(), q.remittanceId(), q.occurredAt(), deviceId, note, pin, strictPin,
                newDate, reason, null);
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
        return new ActionResult(q.id(), SyncOutcome.Status.REJECTED, code, message, null, false);
    }
}
