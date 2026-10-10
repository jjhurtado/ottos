package org.jobits.ottos.remittances.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Customer and beneficiary statistics, computed from their remittances every time (nothing is stored, so they
 * can't drift). Amounts sent are in the source currency (USD); amounts received are grouped by currency.
 */
public interface RemittanceStatsService {

    CustomerStats forCustomer(UUID customerId);

    BeneficiaryStats forBeneficiary(UUID beneficiaryId);

    /**
     * @param open  not yet completed
     * @param late  open and past the expected date
     */
    record CustomerStats(UUID customerId, long remittances, long open, long late, Totals deliveries,
                  PickupTotals pickups, Instant firstAt, Instant lastAt, List<Monthly> lastMonths,
                  List<Ranked> topBeneficiaries, List<Ranked> topMunicipalities) {
    }

    /** Deliveries sent by a customer, in USD: amount sent, fees, total charged and average amount. */
    record Totals(long count, BigDecimal amountSent, BigDecimal fees, BigDecimal charged, BigDecimal averageAmount) {
    }

    record PickupTotals(long count, BigDecimal amountCollected) {
    }

    record Monthly(String month, long count, BigDecimal amount) {
    }

    record Ranked(String key, String name, long count, BigDecimal amount) {
    }

    record CurrencyTotal(String currency, long count, BigDecimal amount) {
    }

    /** received: completed deliveries, by the currency handed over. */
    record BeneficiaryStats(UUID beneficiaryId, long remittances, long open, long late,
                     List<CurrencyTotal> received, PickupTotals pickups, Instant firstAt,
                     Instant lastAt, List<Ranked> topSenders) {
    }
}
