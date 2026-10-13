package org.jobits.ottos.remittances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An action from the mobile app's offline queue that was applied; its id makes resending it harmless. */
@Entity
@Table(name = "synced_actions", schema = "remittances")
public class SyncedAction {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "remittance_id", nullable = false)
    private UUID remittanceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    protected SyncedAction() {
        // required by JPA
    }

    public SyncedAction(UUID id, UUID userId, String type, UUID remittanceId, Instant occurredAt, Instant receivedAt,
                        String deviceId) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.remittanceId = remittanceId;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.deviceId = deviceId;
    }

    /** True if this record is the same action (same user, type and remittance), not another one reusing the id. */
    public boolean isSameAction(UUID userId, String type, UUID remittanceId) {
        return this.userId.equals(userId) && this.type.equals(type) && this.remittanceId.equals(remittanceId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getRemittanceId() {
        return remittanceId;
    }
}
