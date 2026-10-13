package org.jobits.ottos.remittances;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.AuditEvent;
import org.jobits.ottos.beneficiaries.BeneficiaryService;
import org.jobits.ottos.beneficiaries.BeneficiaryService.BeneficiaryInfo;
import org.jobits.ottos.configuration.ConfigurationService;
import org.jobits.ottos.configuration.ConfigurationService.CorridorView;
import org.jobits.ottos.configuration.ConfigurationService.FeeRuleView;
import org.jobits.ottos.configuration.ConfigurationService.RateView;
import org.jobits.ottos.configuration.ServiceType;
import org.jobits.ottos.customers.CustomerService;
import org.jobits.ottos.remittances.RemittanceEvents.RemittanceRegistered;
import org.jobits.ottos.remittances.domain.Remittance;
import org.jobits.ottos.remittances.domain.RemittanceEvent;
import org.jobits.ottos.remittances.domain.RemittanceEventRepository;
import org.jobits.ottos.remittances.domain.RemittanceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.jobits.ottos.configuration.ConfigurationService.BUSINESS_CURRENCY;

/** Implementation of {@link HistoricalImportService}. */
@Service
class HistoricalImportServiceImpl implements HistoricalImportService {

    static final String KEY_PREFIX = "historical:";
    private static final int MAX_REF = 100;

    private final RemittanceRepository remittances;
    private final RemittanceEventRepository events;
    private final CustomerService customers;
    private final BeneficiaryService beneficiaries;
    private final ConfigurationService configuration;
    private final Workflow workflow;
    private final RemittanceCodes codes;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate transactions;
    private final Clock clock;

    HistoricalImportServiceImpl(RemittanceRepository remittances, RemittanceEventRepository events,
                                CustomerService customers, BeneficiaryService beneficiaries,
                                ConfigurationService configuration, Workflow workflow, RemittanceCodes codes,
                                ApplicationEventPublisher publisher, PlatformTransactionManager transactionManager,
                                Clock clock) {
        this.remittances = remittances;
        this.events = events;
        this.customers = customers;
        this.beneficiaries = beneficiaries;
        this.configuration = configuration;
        this.workflow = workflow;
        this.codes = codes;
        this.publisher = publisher;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public ImportResult importHistorical(boolean dryRun, UUID customerId, List<HistoricalRow> rows, UUID actorId) {
        if (customers.find(customerId).isEmpty()) {
            throw ApiException.badRequest("CUSTOMER_UNAVAILABLE", "Unknown customer " + customerId);
        }
        List<RowResult> results = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            HistoricalRow row = rows.get(i);
            int index = i;
            RowResult result;
            try {
                result = transactions.execute(status -> {
                    RowResult r = importOne(index, row, customerId, actorId, dryRun);
                    if (dryRun) {
                        status.setRollbackOnly();
                    }
                    return r;
                });
            } catch (ApiException e) {
                result = new RowResult(index, row.ref(), RowStatus.REJECTED, null, e.getCode(), e.getReason());
            }
            results.add(result);
        }
        Summary summary = new Summary(count(results, RowStatus.CREATED), count(results, RowStatus.EXISTING),
                count(results, RowStatus.REJECTED));
        if (!dryRun) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("customerId", customerId.toString());
            details.put("created", summary.created());
            details.put("existing", summary.existing());
            details.put("rejected", summary.rejected());
            details.put("charged", results.stream().filter(r -> r.status() == RowStatus.CREATED)
                    .map(r -> rows.get(r.index()))
                    .map(r -> r.amountDelivered().add(r.fee()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            publisher.publishEvent(new AuditEvent("HISTORICAL_REMITTANCES_IMPORTED", "REMITTANCES",
                    UUID.randomUUID().toString(), "historical import", actorId, clock.instant(), details));
        }
        return new ImportResult(dryRun, summary, results);
    }

    /** Every check comes before the first write, so a rejection never leaves half a row behind. */
    private RowResult importOne(int index, HistoricalRow row, UUID customerId, UUID actorId, boolean dryRun) {
        String ref = row.ref() == null ? null : row.ref().trim();
        if (ref == null || ref.isEmpty() || ref.length() > MAX_REF) {
            throw invalid("ref is required, with at most " + MAX_REF + " characters");
        }
        Optional<Remittance> existing = remittances.findByIdempotencyKey(KEY_PREFIX + ref);
        if (existing.isPresent()) {
            return new RowResult(index, ref, RowStatus.EXISTING, existing.get().getId(), null, null);
        }
        if (row.occurredAt() == null) {
            throw invalid("occurredAt is required");
        }
        Instant occurredAt = row.occurredAt().atZone(clock.getZone()).toInstant();
        if (occurredAt.isAfter(clock.instant())) {
            throw ApiException.badRequest("INVALID_OCCURRED_AT", "occurredAt must not be in the future");
        }
        BigDecimal amount = money(row.amountDelivered(), "amountDelivered", false);
        BigDecimal fee = money(row.fee(), "fee", true);
        if (fee.compareTo(amount) > 0) {
            throw invalid("fee must not be more than amountDelivered");
        }
        BeneficiaryInfo beneficiary = beneficiary(row.beneficiaryPhone(), row.beneficiaryName());
        CorridorView corridor = configuration.activeCorridor(BUSINESS_CURRENCY, BUSINESS_CURRENCY)
                .orElseThrow(() -> ApiException.conflict("NO_ACTIVE_CORRIDOR", "No active corridor USD-USD"));
        RateView rate = corridor.rate(ServiceType.DELIVERY);
        FeeRuleView feeRule = corridor.feeRule(ServiceType.DELIVERY);
        if (rate == null || feeRule == null) {
            throw ApiException.conflict("NO_EXCHANGE_RATE", "No DELIVERY rate or fee rule set for " + corridor.code());
        }

        BigDecimal total = amount.add(fee);
        // The rate and fee rule in force now, only as references (the columns are required); the fee is the ticket's
        Remittance r = Remittance.imported(codes.newCode(), finalStatus(), customerId,
                new Remittance.BeneficiarySnapshot(beneficiary.id(), beneficiary.fullName(), beneficiary.phone(),
                        beneficiary.alternatePhone(), beneficiary.addressLine(), beneficiary.municipalityCode(),
                        beneficiary.reference()),
                new Remittance.QuoteSnapshot(corridor.code(), BUSINESS_CURRENCY, BUSINESS_CURRENCY, amount, fee, true,
                        total, rate.rate(), false, amount, rate.id(), feeRule.id()),
                codes.newPin(), "Imported from the old records (" + ref + ")", KEY_PREFIX + ref, occurredAt,
                row.occurredAt().toLocalDate(), actorId);
        remittances.saveAndFlush(r);
        events.save(RemittanceEvent.created(r, actorId, occurredAt));
        // The business was paid then: the cash records it on that date. No courier, so no courier's cash moves
        publisher.publishEvent(new RemittanceRegistered(r.getId(), r.getCode(), r.getType(), BUSINESS_CURRENCY, total,
                actorId, occurredAt));
        return new RowResult(index, ref, RowStatus.CREATED, dryRun ? null : r.getId(), null, null);
    }

    /**
     * The beneficiary whose phone ends with the same 8 digits; when several share it, the one with the same name
     * (ignoring case and spaces). Deleted beneficiaries count too: this is their past.
     */
    private BeneficiaryInfo beneficiary(String phone, String name) {
        List<BeneficiaryInfo> found = beneficiaries.findByPhone(phone);
        if (found.isEmpty()) {
            throw ApiException.badRequest("BENEFICIARY_NOT_FOUND", "No beneficiary with phone " + phone);
        }
        if (found.size() > 1 && name != null && !name.isBlank()) {
            List<BeneficiaryInfo> named = found.stream()
                    .filter(b -> squash(b.fullName()).equalsIgnoreCase(squash(name)))
                    .toList();
            found = named.isEmpty() ? found : named;
        }
        if (found.size() > 1) {
            throw ApiException.badRequest("AMBIGUOUS_BENEFICIARY",
                    found.size() + " beneficiaries share phone " + phone + " and the name does not tell them apart");
        }
        return found.getFirst();
    }

    private String finalStatus() {
        List<String> finals = workflow.finalCodes();
        if (finals.isEmpty()) {
            throw new IllegalStateException("No final status configured");
        }
        return finals.getFirst();
    }

    private static BigDecimal money(BigDecimal value, String field, boolean zeroAllowed) {
        if (value == null || (zeroAllowed ? value.signum() < 0 : value.signum() <= 0)
                || value.stripTrailingZeros().scale() > 2) {
            throw invalid(field + (zeroAllowed ? " must be zero or positive" : " must be positive")
                    + " with at most 2 decimals");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String squash(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static long count(List<RowResult> results, RowStatus status) {
        return results.stream().filter(r -> r.status() == status).count();
    }

    private static ApiException invalid(String detail) {
        return ApiException.badRequest("VALIDATION_FAILED", detail);
    }
}
