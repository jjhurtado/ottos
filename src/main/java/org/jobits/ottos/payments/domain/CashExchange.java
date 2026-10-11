package org.jobits.ottos.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A courier changed cash from one currency to another at the rate they got. Its two legs are cash movements inside the
 * courier's cash. rate is always units of the other currency per 1 USD. Never edited, except to resolve its review.
 */
@Entity
@Table(name = "cash_exchanges")
public class CashExchange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "from_currency", nullable = false, length = 3)
    private String fromCurrency;

    @Column(name = "from_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal fromAmount;

    @Column(name = "to_currency", nullable = false, length = 3)
    private String toCurrency;

    @Column(name = "to_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal toAmount;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal rate;

    @Column(name = "configured_rate", precision = 19, scale = 6)
    private BigDecimal configuredRate;

    @Column(name = "deviation_percent", precision = 9, scale = 2)
    private BigDecimal deviationPercent;

    @Column(length = 1000)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "client_action_id")
    private UUID clientActionId;

    @Column(name = "review_reason", length = 40)
    private String reviewReason;

    @Column(name = "review_flagged_at")
    private Instant reviewFlaggedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    protected CashExchange() {
        // required by JPA
    }

    public CashExchange(UUID courierId, String fromCurrency, BigDecimal fromAmount, String toCurrency,
                        BigDecimal toAmount, BigDecimal rate, BigDecimal configuredRate, BigDecimal deviationPercent,
                        String note, Instant occurredAt, UUID recordedBy, Instant recordedAt, UUID clientActionId) {
        this.courierId = courierId;
        this.fromCurrency = fromCurrency;
        this.fromAmount = fromAmount;
        this.toCurrency = toCurrency;
        this.toAmount = toAmount;
        this.rate = rate;
        this.configuredRate = configuredRate;
        this.deviationPercent = deviationPercent;
        this.note = note;
        this.occurredAt = occurredAt;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
        this.clientActionId = clientActionId;
    }

    public void flagForReview(String reason, Instant at) {
        this.reviewReason = reason;
        this.reviewFlaggedAt = at;
    }

    public void resolveReview(Instant at, UUID by, String note) {
        this.resolvedAt = at;
        this.resolvedBy = by;
        this.resolutionNote = note;
    }

    /** Flagged and not resolved yet. */
    public boolean isPendingReview() {
        return reviewReason != null && resolvedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public String getFromCurrency() {
        return fromCurrency;
    }

    public BigDecimal getFromAmount() {
        return fromAmount;
    }

    public String getToCurrency() {
        return toCurrency;
    }

    public BigDecimal getToAmount() {
        return toAmount;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getConfiguredRate() {
        return configuredRate;
    }

    public BigDecimal getDeviationPercent() {
        return deviationPercent;
    }

    public String getNote() {
        return note;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public UUID getClientActionId() {
        return clientActionId;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public Instant getReviewFlaggedAt() {
        return reviewFlaggedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }
}
