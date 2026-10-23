package org.jobits.ottos.remittances;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One remittance of the sales detail (contract: docs/contracts/monthly-report.md, section "Detalle"). It carries no
 * personal data: no customer or beneficiary names, phones, address, reference, PIN, notes, on-the-house reason or
 * who registered it. Amounts in the business currency except amountToDeliver, in targetCurrency.
 *
 * @param collected         see {@link SaleFigures}: amount + fee for a delivery, the cash collected for a pickup
 * @param deliveredCurrency see {@link SaleFigures}: null for a pickup
 * @param withIncident      with an open incident now
 * @param voided            null unless voided; a voided remittance is listed but counts in no total
 */
public record SalesLine(UUID id, String code, RemittanceType type, String status, String statusName,
                        Instant createdAt, LocalDate expectedDate, Instant completedAt, boolean completedFromBusiness,
                        String municipalityCode, String municipalityName, String courierName,
                        String sourceCurrency, String targetCurrency, BigDecimal amount, BigDecimal fee,
                        BigDecimal total, BigDecimal rate, BigDecimal amountToDeliver, BigDecimal collected,
                        BigDecimal deliveredCurrency, boolean onTheHouse, BigDecimal waivedFee, boolean withIncident,
                        RemittanceView.Voided voided) {
}
