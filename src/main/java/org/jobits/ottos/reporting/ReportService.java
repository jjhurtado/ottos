package org.jobits.ottos.reporting;

import org.jobits.ottos.remittances.SalesSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Business reports. Contract of the monthly sales report: docs/contracts/monthly-report.md. */
public interface ReportService {

    /** Remittances registered in that month (business timezone), whatever their status. */
    MonthlyReport monthly(YearMonth month);

    /** The agreed shape: everything in USD except delivered and byCurrency[].delivered. */
    record MonthlyReport(String month, LocalDate from, LocalDate to, Instant generatedAt,
                         SalesSummary.Counts remittances, BigDecimal collected, BigDecimal fees,
                         BigDecimal deliveredUsd, BigDecimal difference, BigDecimal roundingMargin,
                         SalesSummary.Pickups pickups, List<SalesSummary.CurrencyAmount> delivered,
                         List<SalesSummary.ByCurrency> byCurrency, List<SalesSummary.ByType> byType) {
    }
}
