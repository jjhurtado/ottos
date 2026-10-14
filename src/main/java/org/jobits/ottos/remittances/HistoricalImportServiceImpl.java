package org.jobits.ottos.remittances;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.AuditEvent;
import org.jobits.ottos.Texts;
import org.jobits.ottos.beneficiaries.BeneficiaryService;
import org.jobits.ottos.beneficiaries.BeneficiaryService.BeneficiaryInfo;
import org.jobits.ottos.configuration.ConfigurationService;
import org.jobits.ottos.configuration.ConfigurationService.CorridorView;
import org.jobits.ottos.configuration.ConfigurationService.FeeRuleView;
import org.jobits.ottos.configuration.ConfigurationService.RateView;
import org.jobits.ottos.configuration.ServiceType;
import org.jobits.ottos.customers.CustomerService;
import org.jobits.ottos.remittances.domain.Remittance;
import org.jobits.ottos.remittances.domain.RemittanceEvent;
import org.jobits.ottos.remittances.domain.RemittanceEventRepository;
import org.jobits.ottos.remittances.domain.RemittanceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
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
    private final RemittanceViews views;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate transactions;
    private final Clock clock;

    HistoricalImportServiceImpl(RemittanceRepository remittances, RemittanceEventRepository events,
                                CustomerService customers, BeneficiaryService beneficiaries,
                                ConfigurationService configuration, Workflow workflow, RemittanceCodes codes,
                                RemittanceViews views,
                                ApplicationEventPublisher publisher, PlatformTransactionManager transactionManager,
                                Clock clock) {
        this.remittances = remittances;
        this.events = events;
        this.customers = customers;
        this.beneficiaries = beneficiaries;
        this.configuration = configuration;
        this.workflow = workflow;
        this.codes = codes;
        this.views = views;
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
        String business = configuration.businessCurrency();
        CorridorView corridor = configuration.activeCorridor(business, business)
                .orElseThrow(() -> ApiException.conflict("NO_ACTIVE_CORRIDOR", "No active corridor " + business + "-" + business));
        RateView rate = corridor.rate(ServiceType.DELIVERY);
        FeeRuleView feeRule = corridor.feeRule(ServiceType.DELIVERY);
        if (rate == null || feeRule == null) {
            throw ApiException.conflict("NO_EXCHANGE_RATE", "No DELIVERY rate or fee rule set for " + corridor.code());
        }

        BigDecimal total = amount.add(fee);
        // The rate and fee rule in force now, only as references (the columns are required); the fee is the ticket's
        Remittance r = Remittance.imported(codes.newCode(), RemittanceType.DELIVERY, workflow.finalStatus(), customerId,
                new Remittance.BeneficiarySnapshot(beneficiary.id(), beneficiary.fullName(), beneficiary.phone(),
                        beneficiary.alternatePhone(), beneficiary.addressLine(), beneficiary.municipalityCode(),
                        beneficiary.reference()),
                new Remittance.QuoteSnapshot(corridor.code(), business, business, amount, fee, true,
                        total, rate.rate(), false, amount, rate.id(), feeRule.id()),
                codes.newPin(), "Imported from the old records (" + ref + ")", KEY_PREFIX + ref, occurredAt,
                row.occurredAt().toLocalDate(), actorId);
        remittances.saveAndFlush(r);
        events.save(RemittanceEvent.created(r, actorId, occurredAt));
        return new RowResult(index, ref, RowStatus.CREATED, dryRun ? null : r.getId(), null, null);
    }

    @Override
    @Transactional
    public Created create(NewHistorical h, Viewer viewer) {
        String ref = Texts.blankToNull(h.ref());
        if (ref != null && ref.length() > MAX_REF) {
            throw invalid("ref must have at most " + MAX_REF + " characters");
        }
        if (ref != null) {
            Optional<Remittance> existing = remittances.findByIdempotencyKey(KEY_PREFIX + ref);
            if (existing.isPresent()) {
                return new Created(views.of(existing.get(), viewer), false);
            }
        }
        if (h.type() == null || h.occurredAt() == null || h.customerId() == null || h.beneficiaryId() == null) {
            throw invalid("type, customerId, beneficiaryId and occurredAt are required");
        }
        String business = configuration.businessCurrency();
        String currency = h.targetCurrency() == null ? business : h.targetCurrency().trim().toUpperCase();
        boolean sameCurrency = business.equals(currency);
        if (!configuration.currencies().contains(currency)) {
            throw ApiException.badRequest("INVALID_CURRENCY", "targetCurrency must be one of " + configuration.currencies());
        }
        if (h.type() == RemittanceType.PICKUP && !sameCurrency) {
            throw invalid("a pickup is collected in " + business);
        }
        Instant occurredAt = h.occurredAt().atZone(clock.getZone()).toInstant();
        if (occurredAt.isAfter(clock.instant())) {
            throw ApiException.badRequest("INVALID_OCCURRED_AT", "occurredAt must not be in the future");
        }
        BigDecimal amount = money(h.amount(), "amount", false);
        BigDecimal fee = money(h.fee(), "fee", true);
        BigDecimal rate = BigDecimal.ONE;
        BigDecimal toDeliver = amount;
        if (!sameCurrency) {
            if (h.rate() == null || h.rate().signum() <= 0 || h.rate().stripTrailingZeros().scale() > 6) {
                throw invalid("rate is required for " + currency + ": positive, at most 6 decimals");
            }
            rate = h.rate();
            toDeliver = money(h.amountToDeliver(), "amountToDeliver", false);
        }
        if (customers.find(h.customerId()).isEmpty()) {
            throw ApiException.badRequest("CUSTOMER_UNAVAILABLE", "Unknown customer " + h.customerId());
        }
        BeneficiaryInfo beneficiary = beneficiaries.find(h.beneficiaryId())
                .orElseThrow(() -> ApiException.badRequest("BENEFICIARY_UNAVAILABLE", "Unknown beneficiary"));
        ServiceType kind = ServiceType.valueOf(h.type().name());
        CorridorView corridor = configuration.activeCorridor(business, currency)
                .orElseThrow(() -> ApiException.badRequest("NO_ACTIVE_CORRIDOR", "No active corridor " + business + "-" + currency));
        RateView rateRef = corridor.rate(kind);
        FeeRuleView feeRule = corridor.feeRule(kind);
        if (rateRef == null || feeRule == null) {
            throw ApiException.conflict("NO_EXCHANGE_RATE", "No " + kind + " rate or fee rule set for " + corridor.code());
        }

        String note = Texts.blankToNull(h.notes());
        Remittance r = Remittance.imported(codes.newCode(), h.type(), workflow.finalStatus(), h.customerId(),
                new Remittance.BeneficiarySnapshot(beneficiary.id(), beneficiary.fullName(), beneficiary.phone(),
                        beneficiary.alternatePhone(), beneficiary.addressLine(), beneficiary.municipalityCode(),
                        beneficiary.reference()),
                // The configured rate and fee rule only as references; the figures are the ticket's
                new Remittance.QuoteSnapshot(corridor.code(), business, currency, amount, fee, true,
                        amount.add(fee), rate, !sameCurrency, toDeliver, rateRef.id(), feeRule.id()),
                codes.newPin(), note == null ? "Registered by hand as an old remittance" : note,
                ref == null ? null : KEY_PREFIX + ref, occurredAt,
                h.occurredAt().toLocalDate(), viewer.userId());
        remittances.saveAndFlush(r);
        events.save(RemittanceEvent.created(r, viewer.userId(), occurredAt));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", r.getCode());
        details.put("ref", ref);
        details.put("type", r.getType().name());
        details.put("amount", amount);
        details.put("fee", fee);
        details.put("currency", currency);
        details.put("occurredAt", occurredAt.toString());
        publisher.publishEvent(new AuditEvent("HISTORICAL_REMITTANCE_CREATED", "REMITTANCE", r.getId().toString(),
                r.getCode(), viewer.userId(), clock.instant(), details));
        return new Created(views.of(r, viewer), true);
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
