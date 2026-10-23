package org.jobits.ottos.reporting;

import org.jobits.ottos.configuration.ConfigurationService;
import org.jobits.ottos.remittances.RemittanceService;
import org.jobits.ottos.remittances.SalesSummary;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

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
        ZoneId zone = clock.getZone();
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        SalesSummary s = remittances.salesSummary(from, to);
        return new MonthlyReport(month.toString(), month.atDay(1), month.atEndOfMonth(), clock.instant(),
                configuration.businessCurrency(), s.remittances(), s.collected(), s.fees(), s.deliveredCurrency(),
                s.difference(), s.roundingMargin(), s.pickups(), s.onTheHouse(), s.delivered(), s.byCurrency(), s.byType());
    }
}
