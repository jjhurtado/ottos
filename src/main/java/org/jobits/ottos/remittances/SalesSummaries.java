package org.jobits.ottos.remittances;

import org.jobits.ottos.remittances.domain.Remittance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Adds up the {@link SalesSummary} of a set of remittances (contract: docs/contracts/monthly-report.md). */
final class SalesSummaries {

    private SalesSummaries() {
    }

    /** @param to end of the period: "completed" means completed before it */
    static SalesSummary of(List<Remittance> registered, Instant to) {
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal deliveryFees = BigDecimal.ZERO;
        BigDecimal deliveredUsd = BigDecimal.ZERO;
        BigDecimal pickupCollected = BigDecimal.ZERO;
        BigDecimal pickupFees = BigDecimal.ZERO;
        long delivery = 0;
        long completed = 0;
        long withIncident = 0;
        Map<String, CurrencyTotals> byCurrency = new TreeMap<>();
        Map<RemittanceType, TypeTotals> byType = new TreeMap<>();
        for (Remittance r : registered) {
            completed += r.getCompletedAt() != null && r.getCompletedAt().isBefore(to) ? 1 : 0;
            withIncident += r.getOpenIncidentReason() != null ? 1 : 0;
            byType.computeIfAbsent(r.getType(), t -> new TypeTotals()).add(r);
            if (r.getType() == RemittanceType.PICKUP) {
                // Nothing is delivered: the courier collects the amount and the fee is income (the rate is informative)
                pickupCollected = pickupCollected.add(r.getAmount());
                pickupFees = pickupFees.add(r.getFee());
                continue;
            }
            BigDecimal usd = r.getAmountToDeliver().divide(r.getRate(), 2, RoundingMode.HALF_UP);
            collected = collected.add(r.getTotal());
            deliveryFees = deliveryFees.add(r.getFee());
            deliveredUsd = deliveredUsd.add(usd);
            delivery++;
            byCurrency.computeIfAbsent(r.getTargetCurrency(), c -> new CurrencyTotals()).add(r, usd);
        }
        long total = registered.size();
        BigDecimal difference = collected.subtract(deliveredUsd);
        return new SalesSummary(
                new SalesSummary.Counts(total, delivery, total - delivery, completed, total - completed, withIncident),
                collected, deliveryFees.add(pickupFees), deliveredUsd, difference, difference.subtract(deliveryFees),
                new SalesSummary.Pickups(total - delivery, pickupCollected, pickupFees),
                byCurrency.entrySet().stream()
                        .map(e -> new SalesSummary.CurrencyAmount(e.getKey(), e.getValue().delivered)).toList(),
                byCurrency.entrySet().stream().map(e -> e.getValue().view(e.getKey())).toList(),
                byType.entrySet().stream().map(e -> e.getValue().view(e.getKey())).toList());
    }

    /** Running totals of one delivery currency. */
    private static final class CurrencyTotals {
        private long remittances;
        private BigDecimal sent = BigDecimal.ZERO;
        private BigDecimal fees = BigDecimal.ZERO;
        private BigDecimal collected = BigDecimal.ZERO;
        private BigDecimal delivered = BigDecimal.ZERO;
        private BigDecimal deliveredUsd = BigDecimal.ZERO;

        void add(Remittance r, BigDecimal usd) {
            remittances++;
            sent = sent.add(r.getAmount());
            fees = fees.add(r.getFee());
            collected = collected.add(r.getTotal());
            delivered = delivered.add(r.getAmountToDeliver());
            deliveredUsd = deliveredUsd.add(usd);
        }

        SalesSummary.ByCurrency view(String currency) {
            return new SalesSummary.ByCurrency(currency, remittances, sent, fees, collected, delivered, deliveredUsd);
        }
    }

    /** Running totals of one remittance type. */
    private static final class TypeTotals {
        private long remittances;
        private BigDecimal collected = BigDecimal.ZERO;
        private BigDecimal fees = BigDecimal.ZERO;

        void add(Remittance r) {
            remittances++;
            collected = collected.add(r.getType() == RemittanceType.PICKUP ? r.getAmount() : r.getTotal());
            fees = fees.add(r.getFee());
        }

        SalesSummary.ByType view(RemittanceType type) {
            return new SalesSummary.ByType(type, remittances, collected, fees);
        }
    }
}
