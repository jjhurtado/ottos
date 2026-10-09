package org.jobits.ottos.remittances.application;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.beneficiaries.Beneficiaries;
import org.jobits.ottos.branches.Zones;
import org.jobits.ottos.customers.Customers;
import org.jobits.ottos.remittances.RemittanceType;
import org.jobits.ottos.remittances.domain.Remittance;
import org.jobits.ottos.remittances.domain.RemittanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Customer and beneficiary statistics, computed from their remittances every time (nothing is stored, so they
 * can't drift). Amounts sent are in the source currency (USD); amounts received are grouped by currency.
 */
@Service
public class RemittanceStats {

    private static final int MONTHS = 12;
    private static final int TOP = 5;

    private final RemittanceRepository remittances;
    private final Workflow workflow;
    private final Customers customers;
    private final Beneficiaries beneficiaries;
    private final Zones zones;
    private final Clock clock;

    RemittanceStats(RemittanceRepository remittances, Workflow workflow, Customers customers,
                    Beneficiaries beneficiaries, Zones zones, Clock clock) {
        this.remittances = remittances;
        this.workflow = workflow;
        this.customers = customers;
        this.beneficiaries = beneficiaries;
        this.zones = zones;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CustomerStats forCustomer(UUID customerId) {
        if (customers.find(customerId).isEmpty()) {
            throw ApiException.notFound("CUSTOMER_NOT_FOUND", "Customer not found");
        }
        List<Remittance> all = remittances.findByCustomerIdOrderByCreatedAtDesc(customerId);
        List<Remittance> deliveries = ofType(all, RemittanceType.DELIVERY);
        List<Remittance> pickups = ofType(all, RemittanceType.PICKUP);
        Map<String, String> municipalityNames = zones.municipalities(null).stream()
                .collect(Collectors.toMap(Zones.MunicipalityInfo::code, Zones.MunicipalityInfo::name));

        List<Ranked> topBeneficiaries = rank(all, Remittance::getBeneficiaryId, Remittance::getBeneficiaryName);
        List<Ranked> municipalities = rank(all, Remittance::getBeneficiaryMunicipalityCode,
                r -> municipalityNames.get(r.getBeneficiaryMunicipalityCode()));

        return new CustomerStats(customerId, all.size(), open(all), late(all),
                new Totals(deliveries.size(), sum(deliveries, Remittance::getAmount), sum(deliveries, Remittance::getFee),
                        sum(deliveries, Remittance::getTotal), average(deliveries)),
                new PickupTotals(pickups.size(), sum(pickups, Remittance::getAmount)),
                first(all), last(all), byMonth(all), topBeneficiaries, municipalities);
    }

    @Transactional(readOnly = true)
    public BeneficiaryStats forBeneficiary(UUID beneficiaryId) {
        if (beneficiaries.find(beneficiaryId).isEmpty()) {
            throw ApiException.notFound("BENEFICIARY_NOT_FOUND", "Beneficiary not found");
        }
        List<Remittance> all = remittances.findByBeneficiaryIdOrderByCreatedAtDesc(beneficiaryId);
        Set<String> finals = Set.copyOf(workflow.finalCodes());
        List<Remittance> delivered = ofType(all, RemittanceType.DELIVERY).stream()
                .filter(r -> finals.contains(r.getStatus()))
                .toList();
        List<CurrencyTotal> received = delivered.stream()
                .collect(Collectors.groupingBy(Remittance::getTargetCurrency, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> new CurrencyTotal(e.getKey(), e.getValue().size(), sum(e.getValue(), Remittance::getAmountToDeliver)))
                .toList();
        List<Remittance> pickups = ofType(all, RemittanceType.PICKUP);
        List<Ranked> senders = rank(all, Remittance::getCustomerId,
                r -> customers.find(r.getCustomerId()).map(Customers.CustomerInfo::fullName).orElse(null));

        return new BeneficiaryStats(beneficiaryId, all.size(), open(all), late(all), received,
                new PickupTotals(pickups.size(), sum(pickups, Remittance::getAmount)),
                first(all), last(all), senders);
    }

    private long open(List<Remittance> all) {
        Set<String> finals = Set.copyOf(workflow.finalCodes());
        return all.stream().filter(r -> !finals.contains(r.getStatus())).count();
    }

    private long late(List<Remittance> all) {
        Set<String> finals = Set.copyOf(workflow.finalCodes());
        LocalDate today = LocalDate.now(clock);
        return all.stream()
                .filter(r -> !finals.contains(r.getStatus()) && r.getExpectedDate().isBefore(today))
                .count();
    }

    /** Last 12 months including the current one, oldest first; months without remittances count as zero. */
    private List<Monthly> byMonth(List<Remittance> all) {
        YearMonth current = YearMonth.now(clock);
        Map<YearMonth, List<Remittance>> grouped = all.stream()
                .collect(Collectors.groupingBy(r -> YearMonth.from(r.getCreatedAt().atZone(clock.getZone()))));
        return IntStream.rangeClosed(0, MONTHS - 1)
                .mapToObj(i -> current.minusMonths(MONTHS - 1 - i))
                .map(month -> {
                    List<Remittance> in = grouped.getOrDefault(month, List.of());
                    return new Monthly(month.toString(), in.size(), sum(in, Remittance::getAmount));
                })
                .toList();
    }

    /** Groups by key, most remittances first, top 5; amount is the sum of amounts sent (source currency). */
    private static <K> List<Ranked> rank(List<Remittance> all, Function<Remittance, K> key,
                                        Function<Remittance, String> label) {
        Map<K, List<Remittance>> grouped = all.stream().collect(Collectors.groupingBy(key));
        return grouped.entrySet().stream()
                .map(e -> new Ranked(e.getKey().toString(), label.apply(e.getValue().get(0)), e.getValue().size(),
                        sum(e.getValue(), Remittance::getAmount)))
                .sorted(Comparator.comparingLong(Ranked::count).reversed().thenComparing(Ranked::amount, Comparator.reverseOrder()))
                .limit(TOP)
                .toList();
    }

    private static List<Remittance> ofType(List<Remittance> all, RemittanceType type) {
        return all.stream().filter(r -> r.getType() == type).toList();
    }

    private static BigDecimal sum(List<Remittance> list, Function<Remittance, BigDecimal> field) {
        return list.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(List<Remittance> list) {
        return list.isEmpty() ? null
                : sum(list, Remittance::getAmount).divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);
    }

    private static Instant first(List<Remittance> all) {
        return all.isEmpty() ? null : all.get(all.size() - 1).getCreatedAt();
    }

    private static Instant last(List<Remittance> all) {
        return all.isEmpty() ? null : all.get(0).getCreatedAt();
    }

    /**
     * @param open  not yet completed
     * @param late  open and past the expected date
     */
    public record CustomerStats(UUID customerId, long remittances, long open, long late, Totals deliveries,
                                PickupTotals pickups, Instant firstAt, Instant lastAt, List<Monthly> lastMonths,
                                List<Ranked> topBeneficiaries, List<Ranked> topMunicipalities) {
    }

    /** Deliveries sent by a customer, in USD: amount sent, fees, total charged and average amount. */
    public record Totals(long count, BigDecimal amountSent, BigDecimal fees, BigDecimal charged, BigDecimal averageAmount) {
    }

    public record PickupTotals(long count, BigDecimal amountCollected) {
    }

    public record Monthly(String month, long count, BigDecimal amount) {
    }

    public record Ranked(String key, String name, long count, BigDecimal amount) {
    }

    public record CurrencyTotal(String currency, long count, BigDecimal amount) {
    }

    /** received: completed deliveries, by the currency handed over. */
    public record BeneficiaryStats(UUID beneficiaryId, long remittances, long open, long late,
                                   List<CurrencyTotal> received, PickupTotals pickups, Instant firstAt,
                                   Instant lastAt, List<Ranked> topSenders) {
    }
}
