package org.jobits.ottos.reporting;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.jobits.ottos.remittances.SalesLine;
import org.jobits.ottos.remittances.SalesSummary;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Business reports. Contracts: docs/contracts/monthly-report.md (the monthly sales report and its detail) and
 * docs/contracts/statistics.md.
 */
public interface ReportService {

    /** The longest period one statistics request may cover, in days. */
    int MAX_STATISTICS_DAYS = 366;

    /** Remittances registered in that month (business timezone), whatever their status. */
    MonthlyReport monthly(YearMonth month);

    /**
     * The detail of the monthly report: every remittance registered in that month (business timezone), voided ones
     * included and marked, oldest first, without personal data. The lines that are not voided add up to
     * {@link #monthly}.
     */
    MonthlyRemittances monthlyRemittances(YearMonth month);

    /**
     * Sales statistics of the remittances registered from one day to another, both included (business timezone),
     * voided ones left out. At most {@link #MAX_STATISTICS_DAYS} days (VALIDATION_FAILED).
     */
    Statistics statistics(LocalDate from, LocalDate to);

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

    /** remittances: one line per remittance registered in the month, ordered by createdAt, then code. */
    record MonthlyRemittances(String month, LocalDate from, LocalDate to, Instant generatedAt,
                              String businessCurrency, DetailCounts counts, List<SalesLine> remittances) {
    }

    /**
     * @param lines  every line, voided ones included
     * @param voided the voided lines
     * @param total  the rest; total to withIncident are the same as {@link MonthlyReport#remittances()}
     */
    record DetailCounts(long lines, long voided, long total, long delivery, long pickup, long completed, long open,
                        long withIncident) {
    }

    /**
     * Groups: byWeek (ISO weeks, cut to the period and then partial), byMonth (cut the same way), byDayOfWeek (always
     * the seven days, Monday first) and byMunicipality (the beneficiary's, only those with remittances, most
     * remittances first). Each group adds up to totals.
     */
    record Statistics(LocalDate from, LocalDate to, Instant generatedAt, String businessCurrency, Figures totals,
                      List<WeekFigures> byWeek, List<MonthFigures> byMonth, List<DayOfWeekFigures> byDayOfWeek,
                      List<MunicipalityFigures> byMunicipality) {
    }

    /**
     * The figures of a set of remittances, in the business currency. The same rules as the monthly report.
     *
     * @param remittances    registered, not voided: deliveries + pickups
     * @param deliveryAmount the amounts sent in the deliveries
     * @param averageAmount  deliveryAmount / deliveries (2 decimals, half up); 0 without deliveries
     * @param collected      amount + fee of the deliveries
     * @param fees           fees of deliveries and pickups
     * @param deliveredCurrency what the deliveries deliver, converted with each one's own rate
     * @param pickupCollected the cash the couriers collect in the pickups
     * @param onTheHouse     deliveries on the house, also counted in the rest with fee 0
     */
    record Figures(long remittances, long deliveries, long pickups, BigDecimal deliveryAmount,
                   BigDecimal averageAmount, BigDecimal collected, BigDecimal fees, BigDecimal deliveredCurrency,
                   BigDecimal pickupCollected, SalesSummary.OnTheHouse onTheHouse) {
    }

    /** week: ISO week, as 2026-W40; from and to: the days of it inside the period; partial: not the whole week. */
    record WeekFigures(String week, LocalDate from, LocalDate to, boolean partial, @JsonUnwrapped Figures figures) {
    }

    /** month: as 2026-10; from and to: the days of it inside the period; partial: not the whole month. */
    record MonthFigures(String month, LocalDate from, LocalDate to, boolean partial, @JsonUnwrapped Figures figures) {
    }

    record DayOfWeekFigures(DayOfWeek dayOfWeek, @JsonUnwrapped Figures figures) {
    }

    record MunicipalityFigures(String municipalityCode, String municipalityName, @JsonUnwrapped Figures figures) {
    }
}
