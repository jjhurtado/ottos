package org.jobits.ottos.reporting;

import org.jobits.ottos.remittances.RemittanceType;
import org.jobits.ottos.remittances.SalesLine;
import org.jobits.ottos.remittances.SalesSummary;
import org.jobits.ottos.reporting.ReportService.DayOfWeekFigures;
import org.jobits.ottos.reporting.ReportService.Figures;
import org.jobits.ottos.reporting.ReportService.MonthFigures;
import org.jobits.ottos.reporting.ReportService.MunicipalityFigures;
import org.jobits.ottos.reporting.ReportService.WeekFigures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups the sales lines of a period for {@link ReportService#statistics} (contract: docs/contracts/statistics.md).
 * The figures of each line come from the remittances module (the same as the monthly report), so every grouping adds
 * up to the totals.
 */
final class SalesStatistics {

    private final LocalDate from;
    private final LocalDate to;
    private final ZoneId zone;
    private final Totals totals = new Totals();
    private final Map<LocalDate, Totals> byWeek = new LinkedHashMap<>();
    private final Map<YearMonth, Totals> byMonth = new LinkedHashMap<>();
    private final Map<DayOfWeek, Totals> byDayOfWeek = new EnumMap<>(DayOfWeek.class);
    private final Map<String, Totals> byMunicipality = new HashMap<>();
    private final Map<String, String> municipalityNames = new HashMap<>();

    private SalesStatistics(LocalDate from, LocalDate to, ZoneId zone) {
        this.from = from;
        this.to = to;
        this.zone = zone;
        // Every week, month and day of the week of the period, also without remittances, in order
        for (LocalDate monday = weekOf(from); !monday.isAfter(to); monday = monday.plusWeeks(1)) {
            byWeek.put(monday, new Totals());
        }
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            byMonth.put(month, new Totals());
        }
        for (DayOfWeek day : DayOfWeek.values()) {
            byDayOfWeek.put(day, new Totals());
        }
    }

    /** @param lines the lines registered in the period, without the voided ones */
    static SalesStatistics of(LocalDate from, LocalDate to, ZoneId zone, List<SalesLine> lines) {
        SalesStatistics statistics = new SalesStatistics(from, to, zone);
        lines.forEach(statistics::add);
        return statistics;
    }

    private void add(SalesLine line) {
        LocalDate day = line.createdAt().atZone(zone).toLocalDate();
        totals.add(line);
        byWeek.get(weekOf(day)).add(line);
        byMonth.get(YearMonth.from(day)).add(line);
        byDayOfWeek.get(day.getDayOfWeek()).add(line);
        byMunicipality.computeIfAbsent(line.municipalityCode(), c -> new Totals()).add(line);
        municipalityNames.putIfAbsent(line.municipalityCode(), line.municipalityName());
    }

    Figures totals() {
        return totals.view();
    }

    List<WeekFigures> byWeek() {
        List<WeekFigures> weeks = new ArrayList<>();
        byWeek.forEach((monday, t) -> {
            LocalDate first = max(monday, from);
            LocalDate last = min(monday.plusDays(6), to);
            String week = "%d-W%02d".formatted(monday.get(IsoFields.WEEK_BASED_YEAR),
                    monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            weeks.add(new WeekFigures(week, first, last, !first.equals(monday) || !last.equals(monday.plusDays(6)),
                    t.view()));
        });
        return weeks;
    }

    List<MonthFigures> byMonth() {
        List<MonthFigures> months = new ArrayList<>();
        byMonth.forEach((month, t) -> {
            LocalDate first = max(month.atDay(1), from);
            LocalDate last = min(month.atEndOfMonth(), to);
            months.add(new MonthFigures(month.toString(), first, last,
                    !first.equals(month.atDay(1)) || !last.equals(month.atEndOfMonth()), t.view()));
        });
        return months;
    }

    List<DayOfWeekFigures> byDayOfWeek() {
        return byDayOfWeek.entrySet().stream()
                .map(e -> new DayOfWeekFigures(e.getKey(), e.getValue().view()))
                .toList();
    }

    List<MunicipalityFigures> byMunicipality() {
        return byMunicipality.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Totals> e) -> -e.getValue().remittances)
                        .thenComparing(Map.Entry::getKey))
                .map(e -> new MunicipalityFigures(e.getKey(), municipalityNames.get(e.getKey()), e.getValue().view()))
                .toList();
    }

    private static LocalDate weekOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    /** Running figures of one group. */
    private static final class Totals {
        private long remittances;
        private long deliveries;
        private long pickups;
        private BigDecimal deliveryAmount = BigDecimal.ZERO;
        private BigDecimal collected = BigDecimal.ZERO;
        private BigDecimal fees = BigDecimal.ZERO;
        private BigDecimal deliveredCurrency = BigDecimal.ZERO;
        private BigDecimal pickupCollected = BigDecimal.ZERO;
        private long onTheHouse;
        private BigDecimal onTheHouseAmount = BigDecimal.ZERO;
        private BigDecimal waivedFees = BigDecimal.ZERO;

        void add(SalesLine line) {
            remittances++;
            fees = fees.add(line.fee());
            if (line.type() == RemittanceType.PICKUP) {
                pickups++;
                pickupCollected = pickupCollected.add(line.collected());
                return;
            }
            deliveries++;
            deliveryAmount = deliveryAmount.add(line.amount());
            collected = collected.add(line.collected());
            deliveredCurrency = deliveredCurrency.add(line.deliveredCurrency());
            if (line.onTheHouse()) {
                onTheHouse++;
                onTheHouseAmount = onTheHouseAmount.add(line.amount());
                waivedFees = waivedFees.add(line.waivedFee());
            }
        }

        Figures view() {
            BigDecimal average = deliveries == 0 ? BigDecimal.ZERO
                    : deliveryAmount.divide(BigDecimal.valueOf(deliveries), 2, RoundingMode.HALF_UP);
            return new Figures(remittances, deliveries, pickups, deliveryAmount, average, collected, fees,
                    deliveredCurrency, pickupCollected,
                    new SalesSummary.OnTheHouse(onTheHouse, onTheHouseAmount, waivedFees));
        }
    }
}
