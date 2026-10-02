package org.jobits.ottos.remittances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One entry of a remittance's history: who did what and when. Never edited. */
@Entity
@Table(name = "remittance_events")
public class RemittanceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "remittance_id", nullable = false)
    private UUID remittanceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType type;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", length = 30)
    private String toStatus;

    @Column(name = "courier_id")
    private UUID courierId;

    @Column(name = "previous_date")
    private LocalDate previousDate;

    @Column(name = "new_date")
    private LocalDate newDate;

    @Column(length = 1000)
    private String note;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected RemittanceEvent() {
        // required by JPA
    }

    private RemittanceEvent(UUID remittanceId, EventType type, UUID actorId, Instant occurredAt, String note) {
        this.remittanceId = remittanceId;
        this.type = type;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
        this.note = note;
    }

    public static RemittanceEvent created(Remittance r, UUID actorId, Instant at) {
        RemittanceEvent e = new RemittanceEvent(r.getId(), EventType.CREATED, actorId, at, r.getNotes());
        e.toStatus = r.getStatus();
        e.newDate = r.getExpectedDate();
        return e;
    }

    public static RemittanceEvent statusChanged(Remittance r, String fromStatus, UUID actorId, Instant at, String note) {
        RemittanceEvent e = new RemittanceEvent(r.getId(), EventType.STATUS_CHANGED, actorId, at, note);
        e.fromStatus = fromStatus;
        e.toStatus = r.getStatus();
        e.courierId = r.getCourierId();
        return e;
    }

    public static RemittanceEvent postponed(Remittance r, LocalDate previousDate, UUID actorId, Instant at, String reason) {
        RemittanceEvent e = new RemittanceEvent(r.getId(), EventType.POSTPONED, actorId, at, reason);
        e.previousDate = previousDate;
        e.newDate = r.getExpectedDate();
        return e;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRemittanceId() {
        return remittanceId;
    }

    public EventType getType() {
        return type;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public LocalDate getPreviousDate() {
        return previousDate;
    }

    public LocalDate getNewDate() {
        return newDate;
    }

    public String getNote() {
        return note;
    }

    public UUID getActorId() {
        return actorId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
