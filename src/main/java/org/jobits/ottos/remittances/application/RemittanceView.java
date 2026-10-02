package org.jobits.ottos.remittances.application;

import org.jobits.ottos.remittances.RemittanceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A remittance as a given viewer may see it. Fields the viewer is not allowed to see are null:
 * fee, total, rate and amountToDeliver (financials) and pin. cashAmount and cashCurrency are always present:
 * what the courier hands over (delivery) or collects (pickup).
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
        BigDecimal total,
        BigDecimal rate,
        BigDecimal amountToDeliver,
        BigDecimal cashAmount,
        String cashCurrency,
        String pin,
        UUID courierId,
        String courierName,
        LocalDate expectedDate,
        boolean late,
        int postponements,
        String notes,
        Instant createdAt,
        UUID createdBy,
        Instant completedAt) {
}
