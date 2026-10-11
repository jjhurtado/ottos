package org.jobits.ottos.remittances.application;

import org.jobits.ottos.remittances.RemittanceType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sales figures of the remittances registered in a period (contract: docs/contracts/monthly-report.md). Amounts of
 * collected, fees, sent and deliveredUsd are in the source currency (USD); delivered is in each delivery currency.
 *
 * @param collected    amount + fee of every remittance
 * @param fees         fees of deliveries and pickups
 * @param deliveredUsd each amount to deliver divided by its own remittance's rate (2 decimals, half up), summed
 * @param difference   collected − deliveredUsd: the fees plus the rounding margin
 */
public record SalesSummary(Counts remittances, BigDecimal collected, BigDecimal fees, BigDecimal deliveredUsd,
                           BigDecimal difference, List<CurrencyAmount> delivered, List<ByCurrency> byCurrency,
                           List<ByType> byType) {

    /**
     * @param completed    completed before the end of the period
     * @param open         the rest
     * @param withIncident with an open incident now
     */
    public record Counts(long total, long delivery, long pickup, long completed, long open, long withIncident) {
    }

    public record CurrencyAmount(String currency, BigDecimal amount) {
    }

    /** By delivery currency. */
    public record ByCurrency(String currency, long remittances, BigDecimal sent, BigDecimal fees, BigDecimal collected,
                             BigDecimal delivered, BigDecimal deliveredUsd) {
    }

    public record ByType(RemittanceType type, long remittances, BigDecimal collected, BigDecimal fees) {
    }
}
