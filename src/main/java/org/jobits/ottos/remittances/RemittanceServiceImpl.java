package org.jobits.ottos.remittances;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.AuditEvent;
import org.jobits.ottos.beneficiaries.BeneficiaryService;
import org.jobits.ottos.beneficiaries.BeneficiaryService.BeneficiaryInfo;
import org.jobits.ottos.configuration.ConfigurationService;
import org.jobits.ottos.configuration.ServiceType;
import org.jobits.ottos.customers.CustomerService;
import org.jobits.ottos.customers.CustomerService.CustomerInfo;
import org.jobits.ottos.identity.StaffDirectoryService;
import org.jobits.ottos.rates.QuoteService;
import org.jobits.ottos.remittances.RemittanceEvents.RemittanceVoided;
import org.jobits.ottos.remittances.domain.Remittance;
import org.jobits.ottos.remittances.domain.RemittanceEvent;
import org.jobits.ottos.remittances.domain.RemittanceEventRepository;
import org.jobits.ottos.remittances.domain.RemittanceRepository;
import org.jobits.ottos.remittances.domain.RemittanceStatus;
import org.jobits.ottos.remittances.domain.RemittanceTransition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.jobits.ottos.Texts.blankToNull;
import static org.jobits.ottos.remittances.RemittanceOperations.notFound;

/**
 * Implementation of {@link RemittanceService}. Changes shared with the offline queue live in
 * {@link RemittanceOperations}; the report figures in {@link SalesSummaries}; the search query in
 * {@link RemittanceSpecifications}.
 */
@Service
class RemittanceServiceImpl implements RemittanceService {

    static final int FEED_LIMIT = 100;
    static final Duration FEED_SETTLE = Duration.ofSeconds(5);

    private final RemittanceRepository remittances;
    private final RemittanceEventRepository events;
    private final RemittanceOperations operations;
    private final RemittanceCodes codes;
    private final ConfigurationService configuration;
    private final Workflow workflow;
    private final RemittanceViews views;
    private final CustomerService customers;
    private final BeneficiaryService beneficiaries;
    private final QuoteService quotes;
    private final StaffDirectoryService staff;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate transactions;
    private final Clock clock;

    RemittanceServiceImpl(RemittanceRepository remittances, RemittanceEventRepository events,
                          RemittanceOperations operations, RemittanceCodes codes, ConfigurationService configuration,
                          Workflow workflow,
                          RemittanceViews views, CustomerService customers, BeneficiaryService beneficiaries,
                          QuoteService quotes, StaffDirectoryService staff, ApplicationEventPublisher publisher,
                          PlatformTransactionManager transactionManager, Clock clock) {
        this.remittances = remittances;
        this.events = events;
        this.operations = operations;
        this.codes = codes;
        this.configuration = configuration;
        this.workflow = workflow;
        this.views = views;
        this.customers = customers;
        this.beneficiaries = beneficiaries;
        this.quotes = quotes;
        this.staff = staff;
        this.publisher = publisher;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ---------------------------------------------------------------- commands

    /**
     * Not @Transactional: the registration runs in its own transaction so that, when a concurrent request with the
     * same idempotency key wins the race (unique key violation on commit), this one returns the winner's remittance.
     */
    @Override
    public RemittanceView register(NewRemittance request, Viewer viewer) {
        try {
            return transactions.execute(status -> registerOnce(request, viewer));
        } catch (DataIntegrityViolationException e) {
            if (request.idempotencyKey() == null) {
                throw e;
            }
            return remittances.findByIdempotencyKey(request.idempotencyKey())
                    .map(r -> views.of(r, viewer))
                    .orElseThrow(() -> e);
        }
    }

    private RemittanceView registerOnce(NewRemittance request, Viewer viewer) {
        if (request.idempotencyKey() != null) {
            var existing = remittances.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return views.of(existing.get(), viewer);
            }
        }
        CustomerInfo customer = customers.find(request.customerId())
                .filter(CustomerInfo::active)
                .orElseThrow(() -> ApiException.badRequest("CUSTOMER_UNAVAILABLE", "Unknown or inactive customer"));
        BeneficiaryInfo beneficiary = beneficiaries.find(request.beneficiaryId())
                .filter(BeneficiaryInfo::active)
                .orElseThrow(() -> ApiException.badRequest("BENEFICIARY_UNAVAILABLE", "Unknown or inactive beneficiary"));
        // Sending to a beneficiary links them to the customer (beneficiaries loaded without a customer get linked here)
        beneficiaries.link(customer.id(), beneficiary.id());
        QuoteService.Quote quote = quotes.quote(ServiceType.valueOf(request.type().name()),
                configuration.businessCurrency(), request.targetCurrency(), request.amount(), request.rate(),
                request.fee());

        Instant now = clock.instant();
        Remittance remittance = new Remittance(
                codes.newCode(), request.type(), workflow.initial().getCode(), customer.id(),
                new Remittance.BeneficiarySnapshot(beneficiary.id(), beneficiary.fullName(), beneficiary.phone(),
                        beneficiary.alternatePhone(), beneficiary.addressLine(), beneficiary.municipalityCode(),
                        beneficiary.reference()),
                new Remittance.QuoteSnapshot(quote.corridor(), quote.sourceCurrency(), quote.targetCurrency(),
                        quote.amount(), quote.fee(), quote.feeOverridden(), quote.total(), quote.rate(),
                        quote.rateOverridden(),
                        quote.amountToDeliver(), quote.exchangeRateId(), quote.feeRuleId()),
                codes.newPin(), today().plusDays(configuration.defaultDeliveryDays()), blankToNull(request.notes()),
                request.idempotencyKey(), now, viewer.userId());
        remittances.saveAndFlush(remittance);
        events.save(RemittanceEvent.created(remittance, viewer.userId(), now));
        return views.of(remittance, viewer);
    }

    @Override
    @Transactional
    public RemittanceView assign(UUID id, UUID courierId, String note, Viewer viewer) {
        Remittance r = operations.findVisible(id, viewer);
        RemittanceTransition t = workflow.firstTransition(r.getStatus(), RemittanceStatus::isRequiresCourier)
                .orElseThrow(() -> ApiException.conflict("REMITTANCE_NOT_ASSIGNABLE", "Remittance " + r.getCode() + " cannot be assigned in status " + r.getStatus()));
        return operations.apply(r, t, courierId, note, viewer, clock.instant());
    }

    @Override
    @Transactional
    public RemittanceView complete(UUID id, String note, Viewer viewer) {
        Remittance r = operations.findVisible(id, viewer);
        return operations.apply(r, operations.finalTransition(r), null, note, viewer, clock.instant());
    }

    @Override
    @Transactional
    public RemittanceView completeWithPin(UUID id, String pin, String note, Viewer viewer) {
        Remittance r = operations.findVisible(id, viewer);
        if (!r.getPin().equals(pin)) {
            throw ApiException.badRequest("INCORRECT_PIN", "Incorrect PIN");
        }
        return operations.apply(r, operations.finalTransition(r), null, note, viewer, clock.instant());
    }

    @Override
    @Transactional
    public RemittanceView completeFromBusiness(UUID id, String note, Viewer viewer) {
        Remittance r = operations.findVisible(id, viewer);
        return operations.completeFromBusiness(r, note, viewer, clock.instant());
    }

    @Override
    @Transactional
    public RemittanceView transition(UUID id, String toStatus, UUID courierId, String note, Viewer viewer) {
        Remittance r = operations.findVisible(id, viewer);
        RemittanceTransition t = workflow.transition(r.getStatus(), toStatus)
                .orElseThrow(() -> ApiException.conflict("TRANSITION_NOT_ALLOWED", "Cannot move " + r.getCode() + " from " + r.getStatus() + " to " + toStatus));
        return operations.apply(r, t, courierId, note, viewer, clock.instant());
    }

    @Override
    @Transactional
    public RemittanceView reportIncident(UUID id, IncidentReason reason, String note, Viewer viewer) {
        return operations.reportIncident(operations.findVisible(id, viewer), reason, note, viewer, clock.instant());
    }

    @Override
    @Transactional
    public RemittanceView postpone(UUID id, LocalDate newDate, String reason, Viewer viewer) {
        return operations.postpone(operations.findVisible(id, viewer), newDate, reason, viewer, clock.instant());
    }

    @Override
    @Transactional
    public RemittanceView voidRemittance(UUID id, String reason, Viewer viewer) {
        Remittance r = operations.findVisible(id, viewer);
        if (r.isVoided()) {
            throw ApiException.conflict("REMITTANCE_ALREADY_VOIDED", "Remittance " + r.getCode() + " is already voided");
        }
        Instant now = clock.instant();
        String why = reason.trim();
        r.voidIt(now, viewer.userId(), why);
        events.save(RemittanceEvent.voided(r, viewer.userId(), now, why));
        publisher.publishEvent(new RemittanceVoided(r.getId(), r.getCode(), viewer.userId(), now));
        publisher.publishEvent(new AuditEvent("REMITTANCE_VOIDED", "REMITTANCE", r.getId().toString(), r.getCode(),
                viewer.userId(), now, Map.of("code", r.getCode(), "reason", why)));
        return views.of(r, viewer);
    }

    @Override
    @Transactional
    public RemittanceView resolveReview(UUID id, String note, Viewer viewer) {
        Remittance r = operations.findVisible(id, viewer);
        operations.requireNotVoided(r);
        if (r.getReviewReason() == null) {
            throw ApiException.conflict("REVIEW_NOT_PENDING", "Remittance " + r.getCode() + " has nothing to review");
        }
        r.resolveReview();
        events.save(RemittanceEvent.reviewResolved(r, viewer.userId(), clock.instant(), note.trim()));
        return views.of(r, viewer);
    }

    // ---------------------------------------------------------------- queries

    @Override
    @Transactional(readOnly = true)
    public SalesSummary salesSummary(Instant from, Instant to) {
        return SalesSummaries.of(remittances.findByVoidedAtIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to), to);
    }

    @Override
    @Transactional(readOnly = true)
    public RemittanceView get(UUID id, Viewer viewer) {
        return views.of(operations.findVisible(id, viewer), viewer);
    }

    @Override
    public WorkflowView workflow() {
        return workflow.describe();
    }

    @Override
    @Transactional(readOnly = true)
    public RemittanceView getByCode(String code, Viewer viewer) {
        Remittance r = remittances.findByCode(code.trim().toUpperCase())
                .filter(viewer::canSee)
                .orElseThrow(RemittanceOperations::notFound);
        return views.of(r, viewer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventView> history(UUID id, Viewer viewer) {
        operations.findVisible(id, viewer);
        return events.findByRemittanceIdOrderByOccurredAtAsc(id).stream().map(EventView::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RemittanceView> assignedTo(Viewer viewer) {
        return views.of(remittances.findByCourierIdAndVoidedAtIsNullAndStatusNotInOrderByExpectedDateAsc(viewer.userId(),
                workflow.finalCodes()), viewer);
    }

    @Override
    @Transactional(readOnly = true)
    public CompletedFeed completedSince(Instant since, Viewer viewer) {
        // A few seconds behind the clock, so a completion whose transaction is still committing is not skipped
        Instant until = clock.instant().minus(FEED_SETTLE);
        if (since == null || !since.isBefore(until)) {
            return new CompletedFeed(since == null ? until : since, false, List.of());
        }
        List<Remittance> found = remittances
                .findByVoidedAtIsNullAndCompletionRecordedAtGreaterThanAndCompletionRecordedAtLessThanEqualOrderByCompletionRecordedAtAscIdAsc(
                        since, until, PageRequest.of(0, FEED_LIMIT + 1));
        boolean hasMore = found.size() > FEED_LIMIT;
        List<Remittance> page = hasMore ? found.subList(0, FEED_LIMIT) : found;
        Instant next = hasMore ? page.getLast().getCompletionRecordedAt() : until;
        return new CompletedFeed(next, hasMore, views.of(page, viewer));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourierView> couriers() {
        Map<UUID, Long> open = new HashMap<>();
        remittances.countOpenByCourier(workflow.finalCodes()).forEach(row -> open.put((UUID) row[0], (Long) row[1]));
        return staff.couriers().stream()
                .map(m -> new CourierView(m.id(), m.name(), m.email(), open.getOrDefault(m.id(), 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageView search(RemittanceFilter filter, int page, int size, Viewer viewer) {
        Page<Remittance> found = remittances.findAll(
                RemittanceSpecifications.matching(filter, workflow.finalCodes(), today(), clock.getZone()),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageView(views.of(found.getContent(), viewer), found.getNumber(), found.getSize(),
                found.getTotalElements());
    }

    // ---------------------------------------------------------------- helpers

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
