package org.jobits.ottos.remittances;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.identity.StaffDirectoryService;
import org.jobits.ottos.remittances.domain.EventType;
import org.jobits.ottos.remittances.domain.Remittance;
import org.jobits.ottos.remittances.domain.RemittanceEvent;
import org.jobits.ottos.remittances.domain.RemittanceEventRepository;
import org.jobits.ottos.remittances.domain.RemittanceRepository;
import org.jobits.ottos.remittances.domain.SyncedAction;
import org.jobits.ottos.remittances.domain.SyncedActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jobits.ottos.Texts.blankToNull;
import static org.jobits.ottos.remittances.RemittanceOperations.notFound;

/** Implementation of {@link RemittanceSyncService}. */
@Service
class RemittanceSyncServiceImpl implements RemittanceSyncService {

    private static final String POSTPONE_PERMISSION = "remittances:postpone";
    private static final String INCIDENT_PERMISSION = "remittances:report-incident";
    /** A phone clock may run this far ahead of the server's. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);
    /** Synced actions older than this: deliveries are flagged LATE_SYNC, the rest rejected. */
    private static final Duration LATE_SYNC = Duration.ofDays(7);

    private final RemittanceRepository remittances;
    private final RemittanceEventRepository events;
    private final SyncedActionRepository syncedActions;
    private final RemittanceOperations operations;
    private final Workflow workflow;
    private final RemittanceViews views;
    private final Clock clock;

    RemittanceSyncServiceImpl(RemittanceRepository remittances, RemittanceEventRepository events,
                              SyncedActionRepository syncedActions, RemittanceOperations operations, Workflow workflow,
                              RemittanceViews views, Clock clock) {
        this.remittances = remittances;
        this.events = events;
        this.syncedActions = syncedActions;
        this.operations = operations;
        this.workflow = workflow;
        this.views = views;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SyncOutcome applySynced(SyncAction a, Viewer viewer) {
        Optional<SyncedAction> done = syncedActions.findById(a.id());
        if (done.isPresent()) {
            if (!done.get().isSameAction(viewer.userId(), a.type().name(), a.remittanceId())) {
                throw ApiException.conflict("ACTION_ID_CONFLICT", "Action " + a.id() + " was already used for another action");
            }
            return SyncOutcome.alreadyApplied(views.of(remittances.findById(a.remittanceId()).orElseThrow(), viewer));
        }
        Remittance r = remittances.findById(a.remittanceId()).orElseThrow(RemittanceOperations::notFound);
        operations.requireNotVoided(r);
        Instant now = clock.instant();
        Instant at = a.occurredAt();
        if (at.isAfter(now.plus(CLOCK_SKEW)) || at.isBefore(r.getCreatedAt())) {
            throw ApiException.badRequest("INVALID_OCCURRED_AT", "occurredAt must not be in the future nor before the remittance");
        }
        boolean late = at.isBefore(now.minus(LATE_SYNC));

        ReviewReason flag;
        switch (a.type()) {
            case DELIVER, DELIVER_WITH_PIN -> {
                Optional<ReviewReason> outcome = completeSynced(r, a, viewer, late);
                if (outcome.isPresent() && outcome.get() == ReviewReason.DUPLICATE_DELIVERY) {
                    flag(r, ReviewReason.DUPLICATE_DELIVERY, viewer, now, a.id());
                    return SyncOutcome.rejected("REMITTANCE_ALREADY_COMPLETED",
                            "Remittance " + r.getCode() + " was already completed by someone else");
                }
                flag = outcome.orElse(null);
            }
            case POSTPONE -> {
                requireRecentVisible(r, viewer, late, POSTPONE_PERMISSION);
                operations.postpone(r, a.newDate(), a.reason(), viewer, at);
                flag = null;
            }
            case REPORT_INCIDENT -> {
                requireRecentVisible(r, viewer, late, INCIDENT_PERMISSION);
                operations.reportIncident(r, a.incidentReason(), a.note(), viewer, at);
                flag = null;
            }
            default -> throw new IllegalArgumentException("Unknown action " + a.type());
        }
        if (flag != null) {
            flag(r, flag, viewer, now, a.id());
        }
        syncedActions.save(new SyncedAction(a.id(), viewer.userId(), a.type().name(), r.getId(), at, now,
                blankToNull(a.deviceId())));
        return SyncOutcome.applied(views.of(r, viewer), flag != null);
    }

    /**
     * Completes a remittance from a synced delivery. Returns the review it needs, if any; DUPLICATE_DELIVERY means it
     * was already completed by someone else and nothing was applied.
     */
    private Optional<ReviewReason> completeSynced(Remittance r, SyncAction a, Viewer viewer, boolean late) {
        if (!viewer.has(StaffDirectoryService.COURIER_PERMISSION)) {
            throw ApiException.forbidden("FORBIDDEN", "Delivering requires " + StaffDirectoryService.COURIER_PERMISSION);
        }
        if (operations.isCompleted(r)) {
            if (viewer.userId().equals(r.getCompletedBy())) {
                throw ApiException.conflict("REMITTANCE_ALREADY_COMPLETED", "Remittance " + r.getCode() + " is already completed");
            }
            return Optional.of(ReviewReason.DUPLICATE_DELIVERY);
        }
        ReviewReason flag = null;
        if (!viewer.canSee(r)) {
            // Reassigned while the phone was offline: the cash already changed hands, so it counts if it was theirs then
            if (!wasAssignedAt(r, viewer.userId(), a.occurredAt())) {
                throw notFound();
            }
            r.assignTo(viewer.userId());
            flag = ReviewReason.DELIVERED_AFTER_REASSIGNMENT;
        }
        if (a.type() == SyncActionType.DELIVER_WITH_PIN && !r.getPin().equals(a.pin())) {
            if (a.strictPin()) {
                throw ApiException.badRequest("INCORRECT_PIN", "Incorrect PIN");
            }
            flag = flag != null ? flag : ReviewReason.PIN_MISMATCH;
        }
        if (late && flag == null) {
            flag = ReviewReason.LATE_SYNC;
        }
        operations.apply(r, operations.finalTransition(r), null, a.note(), viewer, a.occurredAt());
        return Optional.ofNullable(flag);
    }

    /** Postponing and reporting incidents move no cash: they need the remittance to be theirs now and a recent action. */
    private void requireRecentVisible(Remittance r, Viewer viewer, boolean late, String permission) {
        if (!viewer.has(permission)) {
            throw ApiException.forbidden("FORBIDDEN", "This action requires " + permission);
        }
        if (late) {
            throw ApiException.badRequest("INVALID_OCCURRED_AT", "Only deliveries can be synced more than 7 days later");
        }
        if (!viewer.canSee(r)) {
            throw notFound();
        }
    }

    private boolean wasAssignedAt(Remittance r, UUID userId, Instant at) {
        return events.findFirstByRemittanceIdAndTypeAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                        r.getId(), EventType.STATUS_CHANGED, at)
                .map(e -> userId.equals(e.getCourierId()))
                .orElse(false);
    }

    private void flag(Remittance r, ReviewReason reason, Viewer viewer, Instant now, UUID actionId) {
        r.flagForReview(reason, now, actionId);
        events.save(RemittanceEvent.reviewFlagged(r, reason, viewer.userId(), now));
    }

    @Override
    @Transactional(readOnly = true)
    public AssignedChanges assignedChangesSince(Instant since, Viewer viewer) {
        if (!viewer.has(Viewer.READ_ASSIGNED)) {
            return new AssignedChanges(since == null, List.of(), List.of());
        }
        List<String> finals = workflow.finalCodes();
        if (since == null || since.isBefore(clock.instant().minus(LATE_SYNC))) {
            return new AssignedChanges(true, views.of(
                    remittances.findByCourierIdAndVoidedAtIsNullAndStatusNotInOrderByExpectedDateAsc(viewer.userId(), finals), viewer),
                    List.of());
        }
        List<Remittance> changed = remittances.findByCourierIdAndVoidedAtIsNullAndStatusNotInAndUpdatedAtAfterOrderByExpectedDateAsc(
                viewer.userId(), finals, since);
        List<UUID> removed = remittances.findNoLongerAssignedSince(viewer.userId(), finals, since);
        return new AssignedChanges(false, views.of(changed, viewer), removed);
    }
}
