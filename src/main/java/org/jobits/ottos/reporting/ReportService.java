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

    /**
     * The agreed shape: amounts in businessCurrency (the installation's, USD in Cuba) except delivered and
     * byCurrency[].delivered, in their own currency. deliveredCurrency: what was delivered, converted to the business
     * currency with each remittance's own rate.
     */
    record MonthlyReport(String month, LocalDate from, LocalDate to, Instant generatedAt, String businessCurrency,
                         SalesSummary.Counts remittances, BigDecimal collected, BigDecimal fees,
                         BigDecimal deliveredCurrency, BigDecimal difference, BigDecimal roundingMargin,
                         SalesSummary.Pickups pickups, SalesSummary.OnTheHouse onTheHouse,
                         List<SalesSummary.CurrencyAmount> delivered,
                         List<SalesSummary.ByCurrency> byCurrency, List<SalesSummary.ByType> byType) {
    }
}
