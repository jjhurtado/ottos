package org.jobits.ottos.remittances;

import org.jobits.ottos.remittances.domain.Remittance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Adds up the {@link SalesSummary} of a set of remittances (contract: docs/contracts/monthly-report.md). The figures of
 * each remittance come from {@link SaleFigures}, shared with the detail and the statistics.
 */
final class SalesSummaries {

    private SalesSummaries() {
    }

    /** @param to end of the period: "completed" means completed before it */
    static SalesSummary of(List<Remittance> registered, Instant to) {
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal deliveryFees = BigDecimal.ZERO;
        BigDecimal deliveredCurrency = BigDecimal.ZERO;
        BigDecimal pickupCollected = BigDecimal.ZERO;
        BigDecimal pickupFees = BigDecimal.ZERO;
        long delivery = 0;
        long completed = 0;
        long withIncident = 0;
        long onTheHouse = 0;
        BigDecimal onTheHouseAmount = BigDecimal.ZERO;
        BigDecimal waivedFees = BigDecimal.ZERO;
        Map<String, CurrencyTotals> byCurrency = new TreeMap<>();
        Map<RemittanceType, TypeTotals> byType = new TreeMap<>();
        for (Remittance r : registered) {
            completed += r.getCompletedAt() != null && r.getCompletedAt().isBefore(to) ? 1 : 0;
            withIncident += r.getOpenIncidentReason() != null ? 1 : 0;
            SaleFigures figures = SaleFigures.of(r);
            byType.computeIfAbsent(r.getType(), t -> new TypeTotals()).add(r, figures);
            if (r.getType() == RemittanceType.PICKUP) {
                // Nothing is delivered: the courier collects the amount and the fee is income (the rate is informative)
                pickupCollected = pickupCollected.add(figures.collected());
                pickupFees = pickupFees.add(r.getFee());
                continue;
            }
            if (r.isOnTheHouse()) {
                // Free of fee: it still counts below with fee 0 and total = amount
                onTheHouse++;
                onTheHouseAmount = onTheHouseAmount.add(r.getAmount());
                waivedFees = waivedFees.add(r.getWaivedFee());
            }
            BigDecimal inBusiness = figures.deliveredCurrency();
            collected = collected.add(figures.collected());
            deliveryFees = deliveryFees.add(r.getFee());
            deliveredCurrency = deliveredCurrency.add(inBusiness);
            delivery++;
            byCurrency.computeIfAbsent(r.getTargetCurrency(), c -> new CurrencyTotals()).add(r, inBusiness);
        }
        long total = registered.size();
        BigDecimal difference = collected.subtract(deliveredCurrency);
        return new SalesSummary(
                new SalesSummary.Counts(total, delivery, total - delivery, completed, total - completed, withIncident),
                collected, deliveryFees.add(pickupFees), deliveredCurrency, difference, difference.subtract(deliveryFees),
                new SalesSummary.Pickups(total - delivery, pickupCollected, pickupFees),
                new SalesSummary.OnTheHouse(onTheHouse, onTheHouseAmount, waivedFees),
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
        private BigDecimal deliveredCurrency = BigDecimal.ZERO;

        void add(Remittance r, BigDecimal inBusiness) {
            remittances++;
            sent = sent.add(r.getAmount());
            fees = fees.add(r.getFee());
            collected = collected.add(r.getTotal());
            delivered = delivered.add(r.getAmountToDeliver());
            deliveredCurrency = deliveredCurrency.add(inBusiness);
        }

        SalesSummary.ByCurrency view(String currency) {
            return new SalesSummary.ByCurrency(currency, remittances, sent, fees, collected, delivered, deliveredCurrency);
        }
    }

    /** Running totals of one remittance type. */
    private static final class TypeTotals {
        private long remittances;
        private BigDecimal collected = BigDecimal.ZERO;
        private BigDecimal fees = BigDecimal.ZERO;

        void add(Remittance r, SaleFigures figures) {
            remittances++;
            collected = collected.add(figures.collected());
            fees = fees.add(r.getFee());
        }

        SalesSummary.ByType view(RemittanceType type) {
            return new SalesSummary.ByType(type, remittances, collected, fees);
        }
    }
}
