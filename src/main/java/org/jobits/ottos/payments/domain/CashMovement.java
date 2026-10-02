package org.jobits.ottos.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Money moving from one account to another. Never edited; a mistake is fixed with an opposite movement. */
@Entity
@Table(name = "cash_movements")
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MovementType type;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "remittance_id")
    private UUID remittanceId;

    @Column(length = 1000)
    private String note;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected CashMovement() {
        // required by JPA
    }

    public CashMovement(MovementType type, CashAccount from, CashAccount to, BigDecimal amount, UUID remittanceId,
                        String note, UUID actorId, Instant occurredAt) {
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new IllegalArgumentException("Accounts in different currencies");
        }
        this.type = type;
        this.fromAccountId = from.getId();
        this.toAccountId = to.getId();
        this.amount = amount;
        this.currency = from.getCurrency();
        this.remittanceId = remittanceId;
        this.note = note;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public MovementType getType() {
        return type;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getRemittanceId() {
        return remittanceId;
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
