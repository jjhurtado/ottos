package org.jobits.ottos.reporting;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.configuration.ConfigurationService;
import org.jobits.ottos.remittances.RemittanceService;
import org.jobits.ottos.remittances.SalesDetail;
import org.jobits.ottos.remittances.SalesLine;
import org.jobits.ottos.remittances.SalesSummary;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Implementation of {@link ReportService}. The figures come from the remittances module, which owns the data. */
@Service
class ReportServiceImpl implements ReportService {

    private final RemittanceService remittances;
    private final ConfigurationService configuration;
    private final Clock clock;

    ReportServiceImpl(RemittanceService remittances, ConfigurationService configuration, Clock clock) {
        this.remittances = remittances;
        this.configuration = configuration;
        this.clock = clock;
    }

    @Override
    public MonthlyReport monthly(YearMonth month) {
        SalesSummary s = remittances.salesSummary(startOf(month.atDay(1)), startOf(month.plusMonths(1).atDay(1)));
        return new MonthlyReport(month.toString(), month.atDay(1), month.atEndOfMonth(), clock.instant(),
                configuration.businessCurrency(), s.remittances(), s.collected(), s.fees(), s.deliveredCurrency(),
                s.difference(), s.roundingMargin(), s.pickups(), s.onTheHouse(), s.delivered(), s.byCurrency(), s.byType());
    }

    @Override
    public MonthlyRemittances monthlyRemittances(YearMonth month) {
        SalesDetail detail = remittances.salesDetail(startOf(month.atDay(1)), startOf(month.plusMonths(1).atDay(1)));
        SalesSummary.Counts c = detail.summary();
        return new MonthlyRemittances(month.toString(), month.atDay(1), month.atEndOfMonth(), clock.instant(),
                configuration.businessCurrency(),
                new DetailCounts(detail.lines().size(), detail.voided(), c.total(), c.delivery(), c.pickup(),
                        c.completed(), c.open(), c.withIncident()),
                detail.lines());
    }

    @Override
    public Statistics statistics(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("VALIDATION_FAILED", "from must not be after to");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_STATISTICS_DAYS) {
            throw ApiException.badRequest("VALIDATION_FAILED",
                    "The period may cover at most " + MAX_STATISTICS_DAYS + " days");
        }
        List<SalesLine> lines = remittances.salesDetail(startOf(from), startOf(to.plusDays(1))).lines().stream()
                .filter(line -> line.voided() == null)
                .toList();
        SalesStatistics s = SalesStatistics.of(from, to, clock.getZone(), lines);
        return new Statistics(from, to, clock.instant(), configuration.businessCurrency(), s.totals(), s.byWeek(),
                s.byMonth(), s.byDayOfWeek(), s.byMunicipality());
    }

    private Instant startOf(LocalDate day) {
        ZoneId zone = clock.getZone();
        return day.atStartOfDay(zone).toInstant();
    }
}
