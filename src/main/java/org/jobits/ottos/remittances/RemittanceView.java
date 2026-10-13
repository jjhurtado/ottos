package org.jobits.ottos.remittances;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A remittance as a given viewer may see it. Fields the viewer is not allowed to see are null:
 * fee, total, rate and amountToDeliver (financials) and pin. cashAmount and cashCurrency are always present:
 * what the courier hands over (delivery) or collects (pickup). incident is the open incident and review
 * the pending review, null when there is none.
 */
public record RemittanceView(
        UUID id,
        String code,
        RemittanceType type,
        String status,
        String statusName,
        UUID customerId,
        String customerName,
        String customerPhone,
        UUID beneficiaryId,
        String beneficiaryName,
        String beneficiaryPhone,
        String beneficiaryAlternatePhone,
        String beneficiaryAddress,
        String municipalityCode,
        String municipalityName,
        String beneficiaryReference,
        String sourceCurrency,
        String targetCurrency,
        BigDecimal amount,
        BigDecimal fee,
        Boolean feeOverridden,
        BigDecimal total,
        BigDecimal rate,
        Boolean rateOverridden,
        BigDecimal amountToDeliver,
        BigDecimal cashAmount,
        String cashCurrency,
        String pin,
        UUID courierId,
        String courierName,
        LocalDate expectedDate,
        boolean late,
        int postponements,
        OpenIncident incident,
        PendingReview review,
        String notes,
        Instant createdAt,
        UUID createdBy,
        Instant completedAt,
        Voided voided) {

    /** Voided: it no longer counts anywhere and the cash it moved was reversed. null when it is in force. */
    public record Voided(Instant at, UUID by, String byName, String reason) {
    }

    /** A delivery attempt that failed and nobody has acted on yet. */
    public record OpenIncident(IncidentReason reason, String note, Instant reportedAt, UUID reportedBy) {
    }

    /** Applied from the mobile app but needs Sales to check it. actionId is the synced action that caused it. */
    public record PendingReview(ReviewReason reason, Instant flaggedAt, UUID actionId) {
    }
}
