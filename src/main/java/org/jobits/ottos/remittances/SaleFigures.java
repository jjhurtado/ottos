package org.jobits.ottos.remittances;

import org.jobits.ottos.remittances.domain.Remittance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The sales figures of one remittance, the same for the monthly summary, its detail and the statistics (contract:
 * docs/contracts/monthly-report.md). Business currency.
 *
 * @param collected         a delivery: amount + fee; a pickup: the cash the courier collects (its amount)
 * @param deliveredCurrency a delivery: its amount to deliver divided by its own rate (2 decimals, half up); a pickup
 *                          delivers nothing (its rate is informative): null
 */
record SaleFigures(BigDecimal collected, BigDecimal deliveredCurrency) {

    static SaleFigures of(Remittance r) {
        if (r.getType() == RemittanceType.PICKUP) {
            return new SaleFigures(r.getAmount(), null);
        }
        return new SaleFigures(r.getTotal(), r.getAmountToDeliver().divide(r.getRate(), 2, RoundingMode.HALF_UP));
    }
}
