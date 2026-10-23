package org.jobits.ottos.remittances;


import java.math.BigDecimal;
import java.util.List;

/**
 * Sales figures of the remittances registered in a period (contract: docs/contracts/monthly-report.md). Amounts of
 * collected, fees, sent and deliveredCurrency are in the business currency; delivered is in each delivery currency.
 * A pickup delivers nothing (its rate is informative): it only counts in fees and in pickups.
 *
 * @param collected      amount + fee of every delivery
 * @param fees           fees of deliveries and pickups: the business's earnings
 * @param deliveredCurrency   each delivery's amount to deliver divided by its own rate (2 decimals, half up), summed
 * @param difference     collected − deliveredCurrency: the delivery fees plus the rounding margin
 * @param roundingMargin difference − delivery fees
 */
public record SalesSummary(Counts remittances, BigDecimal collected, BigDecimal fees, BigDecimal deliveredCurrency,
                           BigDecimal difference, BigDecimal roundingMargin, Pickups pickups, OnTheHouse onTheHouse,
                           List<CurrencyAmount> delivered, List<ByCurrency> byCurrency, List<ByType> byType) {

    /**
     * Deliveries on the house (free of fee; docs/contracts/on-the-house.md), also counted in the rest of the figures
     * with fee 0. amount: what was sent; waivedFees: the fees not charged. Business currency.
     */
    public record OnTheHouse(long remittances, BigDecimal amount, BigDecimal waivedFees) {
    }

    /** collected: the cash couriers collect (amount, business currency); fees: entered by hand or from the rule, income only. */
    public record Pickups(long remittances, BigDecimal collected, BigDecimal fees) {
    }

    /**
     * @param completed    completed before the end of the period
     * @param open         the rest
     * @param withIncident with an open incident now
     */
    public record Counts(long total, long delivery, long pickup, long completed, long open, long withIncident) {
    }

    public record CurrencyAmount(String currency, BigDecimal amount) {
    }

    /** Deliveries by delivery currency. */
    public record ByCurrency(String currency, long remittances, BigDecimal sent, BigDecimal fees, BigDecimal collected,
                             BigDecimal delivered, BigDecimal deliveredCurrency) {
    }

    /** collected: amount + fee for deliveries; the cash collected (amount) for pickups. */
    public record ByType(RemittanceType type, long remittances, BigDecimal collected, BigDecimal fees) {
    }
}
