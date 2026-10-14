package org.jobits.ottos.remittances;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.identity.StaffDirectoryService;
import org.jobits.ottos.remittances.RemittanceEvents.RemittanceCompleted;
import org.jobits.ottos.remittances.domain.Remittance;
import org.jobits.ottos.remittances.domain.RemittanceEvent;
import org.jobits.ottos.remittances.domain.RemittanceEventRepository;
import org.jobits.ottos.remittances.domain.RemittanceRepository;
import org.jobits.ottos.remittances.domain.RemittanceStatus;
import org.jobits.ottos.remittances.domain.RemittanceTransition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.jobits.ottos.Texts.blankToNull;

/**
 * The changes to a remittance shared by the online endpoints ({@link RemittanceServiceImpl}) and the offline queue
 * ({@link RemittanceSyncServiceImpl}): moving it through a transition, postponing it and reporting an incident, each
 * recorded in its history at the moment it happened. Callers run inside their own transaction.
 */
@Component
class RemittanceOperations {

    private final RemittanceRepository remittances;
    private final RemittanceEventRepository events;
    private final Workflow workflow;
    private final RemittanceViews views;
    private final StaffDirectoryService staff;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    RemittanceOperations(RemittanceRepository remittances, RemittanceEventRepository events, Workflow workflow,
                         RemittanceViews views, StaffDirectoryService staff, ApplicationEventPublisher publisher,
                         Clock clock) {
        this.remittances = remittances;
        this.events = events;
        this.workflow = workflow;
        this.views = views;
        this.staff = staff;
        this.publisher = publisher;
        this.clock = clock;
    }

    Remittance findVisible(UUID id, Viewer viewer) {
        return remittances.findById(id).filter(viewer::canSee).orElseThrow(RemittanceOperations::notFound);
    }

    /**
     * Moves the remittance through the transition at the moment it happened: assigns the courier when the target
     * status requires one, and on a final status completes it and lets the cash module move the money.
     */
    RemittanceView apply(Remittance r, RemittanceTransition t, UUID courierId, String note, Viewer viewer, Instant at) {
        requireNotVoided(r);
        if (!viewer.has(t.getPermissionCode())) {
            throw ApiException.forbidden("TRANSITION_FORBIDDEN", "Moving to " + t.getToStatus() + " requires " + t.getPermissionCode());
        }
        RemittanceStatus target = workflow.status(t.getToStatus());
        if (target.isRequiresCourier()) {
            if (courierId == null) {
                throw ApiException.badRequest("COURIER_REQUIRED", "Status " + target.getCode() + " requires a courier");
            }
            if (!staff.isCourier(courierId)) {
                throw ApiException.badRequest("INVALID_COURIER", "The courier must be an active user with permission "
                        + StaffDirectoryService.COURIER_PERMISSION + ", not an administrator");
            }
            r.assignTo(courierId);
        }
        if (target.isFinalStatus()) {
            if (r.getCourierId() == null) {
                throw ApiException.conflict("COURIER_NOT_ASSIGNED", "Assign a courier before completing " + r.getCode());
            }
            r.complete(at, viewer.userId(), clock.instant());
            publisher.publishEvent(new RemittanceCompleted(r.getId(), r.getCode(), r.getType(), r.getCourierId(),
                    r.cashAmount(), r.cashCurrency(), viewer.userId(), at));
        }
        String from = r.getStatus();
        r.moveTo(target.getCode());
        events.save(RemittanceEvent.statusChanged(r, from, viewer.userId(), at, blankToNull(note)));
        return views.of(r, viewer);
    }

    /**
     * Sales or Admin hand over (delivery) or receive (pickup) the cash themselves: the remittance reaches the final
     * status from any open status, without a courier (an assigned one is taken off), and the business cash moves.
     */
    RemittanceView completeFromBusiness(Remittance r, String note, Viewer viewer, Instant at) {
        requireOpen(r);
        r.assignTo(null);
        r.complete(at, viewer.userId(), clock.instant());
        publisher.publishEvent(new RemittanceCompleted(r.getId(), r.getCode(), r.getType(), null,
                r.cashAmount(), r.cashCurrency(), viewer.userId(), at));
        String from = r.getStatus();
        r.moveTo(workflow.finalStatus());
        events.save(RemittanceEvent.statusChanged(r, from, viewer.userId(), at, blankToNull(note)));
        return views.of(r, viewer);
    }

    RemittanceTransition finalTransition(Remittance r) {
        return workflow.firstTransition(r.getStatus(), RemittanceStatus::isFinalStatus)
                .orElseThrow(() -> ApiException.conflict("REMITTANCE_NOT_COMPLETABLE", "Remittance " + r.getCode() + " cannot be completed in status " + r.getStatus()));
    }

    /** The new date must be after the current one and not before the day the action happened (business timezone). */
    RemittanceView postpone(Remittance r, LocalDate newDate, String reason, Viewer viewer, Instant at) {
        requireOpen(r);
        if (!newDate.isAfter(r.getExpectedDate()) || newDate.isBefore(LocalDate.ofInstant(at, clock.getZone()))) {
            throw ApiException.badRequest("INVALID_POSTPONE_DATE", "The new date must be after " + r.getExpectedDate() + " and not in the past");
        }
        LocalDate previous = r.getExpectedDate();
        r.postpone(newDate);
        events.save(RemittanceEvent.postponed(r, previous, viewer.userId(), at, reason.trim()));
        return views.of(r, viewer);
    }

    RemittanceView reportIncident(Remittance r, IncidentReason reason, String note, Viewer viewer, Instant at) {
        requireOpen(r);
        if (r.getCourierId() == null) {
            throw ApiException.conflict("COURIER_NOT_ASSIGNED", "Assign a courier before reporting an incident on " + r.getCode());
        }
        String cleanNote = blankToNull(note);
        if (reason == IncidentReason.OTHER && cleanNote == null) {
            throw ApiException.badRequest("INCIDENT_NOTE_REQUIRED", "Explain the incident in the note when the reason is OTHER");
        }
        r.reportIncident(reason, cleanNote, at, viewer.userId());
        events.save(RemittanceEvent.incidentReported(r, reason, viewer.userId(), at, cleanNote));
        return views.of(r, viewer);
    }

    boolean isCompleted(Remittance r) {
        return workflow.status(r.getStatus()).isFinalStatus();
    }

    /** A voided remittance is kept only for the record: nothing changes it any more. */
    void requireNotVoided(Remittance r) {
        if (r.isVoided()) {
            throw ApiException.conflict("REMITTANCE_VOIDED", "Remittance " + r.getCode() + " was voided");
        }
    }

    private void requireOpen(Remittance r) {
        requireNotVoided(r);
        if (isCompleted(r)) {
            throw ApiException.conflict("REMITTANCE_ALREADY_COMPLETED", "Remittance " + r.getCode() + " is already completed");
        }
    }

    static ApiException notFound() {
        return ApiException.notFound("REMITTANCE_NOT_FOUND", "Remittance not found");
    }

}
