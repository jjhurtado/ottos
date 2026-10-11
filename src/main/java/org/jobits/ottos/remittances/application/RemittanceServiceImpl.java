package org.jobits.ottos.remittances.application;

import jakarta.persistence.criteria.Predicate;
import org.jobits.ottos.ApiException;
import org.jobits.ottos.beneficiaries.BeneficiaryService;
import org.jobits.ottos.beneficiaries.BeneficiaryService.BeneficiaryInfo;
import org.jobits.ottos.configuration.ConfigurationService;
import org.jobits.ottos.configuration.ServiceType;
import org.jobits.ottos.customers.CustomerService;
import org.jobits.ottos.customers.CustomerService.CustomerInfo;
import org.jobits.ottos.identity.StaffDirectoryService;
import org.jobits.ottos.rates.QuoteService;
import org.jobits.ottos.remittances.RemittanceEvents.RemittanceCompleted;
import org.jobits.ottos.remittances.RemittanceEvents.RemittanceRegistered;
import org.jobits.ottos.remittances.IncidentReason;
import org.jobits.ottos.remittances.ReviewReason;
import org.jobits.ottos.remittances.RemittanceType;
import org.jobits.ottos.remittances.domain.Remittance;
import org.jobits.ottos.remittances.domain.EventType;
import org.jobits.ottos.remittances.domain.RemittanceEvent;
import org.jobits.ottos.remittances.domain.RemittanceEventRepository;
import org.jobits.ottos.remittances.domain.RemittanceRepository;
import org.jobits.ottos.remittances.domain.RemittanceStatus;
import org.jobits.ottos.remittances.domain.RemittanceTransition;
import org.jobits.ottos.remittances.domain.SyncedAction;
import org.jobits.ottos.remittances.domain.SyncedActionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Implementation of {@link RemittanceService}. */
@Service
class RemittanceServiceImpl implements RemittanceService {

    static final String SOURCE_CURRENCY = "USD";
    private static final String COURIER_PERMISSION = "remittances:deliver";
    private static final String POSTPONE_PERMISSION = "remittances:postpone";
    private static final String INCIDENT_PERMISSION = "remittances:report-incident";
    /** A phone clock may run this far ahead of the server's. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);
    /** Synced actions older than this: deliveries are flagged LATE_SYNC, the rest rejected. */
    private static final Duration LATE_SYNC = Duration.ofDays(7);
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RemittanceRepository remittances;
    private final RemittanceEventRepository events;
    private final SyncedActionRepository syncedActions;
    private final ConfigurationService configuration;
    private final Workflow workflow;
    private final RemittanceViews views;
    private final CustomerService customers;
    private final BeneficiaryService beneficiaries;
    private final QuoteService quotes;
    private final StaffDirectoryService staff;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    RemittanceServiceImpl(RemittanceRepository remittances, RemittanceEventRepository events,
                          SyncedActionRepository syncedActions, ConfigurationService configuration, Workflow workflow,
                          RemittanceViews views, CustomerService customers, BeneficiaryService beneficiaries,
                          QuoteService quotes, StaffDirectoryService staff, ApplicationEventPublisher publisher,
                          Clock clock) {
        this.remittances = remittances;
        this.events = events;
        this.syncedActions = syncedActions;
        this.configuration = configuration;
        this.workflow = workflow;
        this.views = views;
        this.customers = customers;
        this.beneficiaries = beneficiaries;
        this.quotes = quotes;
        this.staff = staff;
        this.publisher = publisher;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- commands

    @Override
    @Transactional
    public RemittanceView register(NewRemittance request, Viewer viewer) {
        if (request.idempotencyKey() != null) {
            var existing = remittances.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return views.of(existing.get(), viewer);
            }
        }
        CustomerInfo customer = customers.find(request.customerId())
                .filter(CustomerInfo::active)
                .orElseThrow(() -> badRequest("CUSTOMER_UNAVAILABLE", "Unknown or inactive customer"));
        BeneficiaryInfo beneficiary = beneficiaries.find(request.beneficiaryId())
                .filter(BeneficiaryInfo::active)
                .orElseThrow(() -> badRequest("BENEFICIARY_UNAVAILABLE", "Unknown or inactive beneficiary"));
        // Sending to a beneficiary links them to the customer (beneficiaries loaded without a customer get linked here)
        beneficiaries.link(customer.id(), beneficiary.id());
        QuoteService.Quote quote = quotes.quote(ServiceType.valueOf(request.type().name()), SOURCE_CURRENCY,
                request.targetCurrency(), request.amount(), request.rate());

        Instant now = clock.instant();
        Remittance remittance = new Remittance(
                newCode(), request.type(), workflow.initial().getCode(), customer.id(),
                new Remittance.BeneficiarySnapshot(beneficiary.id(), beneficiary.fullName(), beneficiary.phone(),
                        beneficiary.alternatePhone(), beneficiary.addressLine(), beneficiary.municipalityCode(),
                        beneficiary.reference()),
                new Remittance.QuoteSnapshot(quote.corridor(), quote.sourceCurrency(), quote.targetCurrency(),
                        quote.amount(), quote.fee(), quote.total(), quote.rate(), quote.rateOverridden(),
                        quote.amountToDeliver(), quote.exchangeRateId(), quote.feeRuleId()),
                newPin(), today().plusDays(configuration.defaultDeliveryDays()), blankToNull(request.notes()),
                request.idempotencyKey(), now, viewer.userId());
        remittances.saveAndFlush(remittance);
        events.save(RemittanceEvent.created(remittance, viewer.userId(), now));
        BigDecimal charged = remittance.getType() == RemittanceType.DELIVERY ? remittance.getTotal() : BigDecimal.ZERO;
        publisher.publishEvent(new RemittanceRegistered(remittance.getId(), remittance.getCode(), remittance.getType(),
                remittance.getSourceCurrency(), charged, viewer.userId(), now));
        return views.of(remittance, viewer);
    }

    @Override
    @Transactional
    public RemittanceView assign(UUID id, UUID courierId, String note, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        RemittanceTransition t = workflow.firstTransition(r.getStatus(), RemittanceStatus::isRequiresCourier)
                .orElseThrow(() -> conflict("REMITTANCE_NOT_ASSIGNABLE", "Remittance " + r.getCode() + " cannot be assigned in status " + r.getStatus()));
        return apply(r, t, courierId, note, viewer, clock.instant());
    }

    @Override
    @Transactional
    public RemittanceView complete(UUID id, String note, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        return apply(r, finalTransition(r), null, note, viewer, clock.instant());
    }

    @Override
    @Transactional
    public RemittanceView completeWithPin(UUID id, String pin, String note, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        if (!r.getPin().equals(pin)) {
            throw badRequest("INCORRECT_PIN", "Incorrect PIN");
        }
        return apply(r, finalTransition(r), null, note, viewer, clock.instant());
    }

    @Override
    @Transactional
    public RemittanceView transition(UUID id, String toStatus, UUID courierId, String note, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        RemittanceTransition t = workflow.transition(r.getStatus(), toStatus)
                .orElseThrow(() -> conflict("TRANSITION_NOT_ALLOWED", "Cannot move " + r.getCode() + " from " + r.getStatus() + " to " + toStatus));
        return apply(r, t, courierId, note, viewer, clock.instant());
    }

    @Override
    @Transactional
    public RemittanceView reportIncident(UUID id, IncidentReason reason, String note, Viewer viewer) {
        return reportIncident(findVisible(id, viewer), reason, note, viewer, clock.instant());
    }

    private RemittanceView reportIncident(Remittance r, IncidentReason reason, String note, Viewer viewer, Instant at) {
        if (workflow.status(r.getStatus()).isFinalStatus()) {
            throw conflict("REMITTANCE_ALREADY_COMPLETED", "Remittance " + r.getCode() + " is already completed");
        }
        if (r.getCourierId() == null) {
            throw conflict("COURIER_NOT_ASSIGNED", "Assign a courier before reporting an incident on " + r.getCode());
        }
        String cleanNote = blankToNull(note);
        if (reason == IncidentReason.OTHER && cleanNote == null) {
            throw badRequest("INCIDENT_NOTE_REQUIRED", "Explain the incident in the note when the reason is OTHER");
        }
        r.reportIncident(reason, cleanNote, at, viewer.userId());
        events.save(RemittanceEvent.incidentReported(r, reason, viewer.userId(), at, cleanNote));
        return views.of(r, viewer);
    }

    @Override
    @Transactional
    public SyncOutcome applySynced(SyncAction a, Viewer viewer) {
        Optional<SyncedAction> done = syncedActions.findById(a.id());
        if (done.isPresent()) {
            if (!done.get().isSameAction(viewer.userId(), a.type().name(), a.remittanceId())) {
                throw conflict("ACTION_ID_CONFLICT", "Action " + a.id() + " was already used for another action");
            }
            return SyncOutcome.alreadyApplied(views.of(remittances.findById(a.remittanceId()).orElseThrow(), viewer));
        }
        Remittance r = remittances.findById(a.remittanceId()).orElseThrow(RemittanceServiceImpl::notFound);
        Instant now = clock.instant();
        Instant at = a.occurredAt();
        if (at.isAfter(now.plus(CLOCK_SKEW)) || at.isBefore(r.getCreatedAt())) {
            throw badRequest("INVALID_OCCURRED_AT", "occurredAt must not be in the future nor before the remittance");
        }
        boolean late = at.isBefore(now.minus(LATE_SYNC));

        ReviewReason flag;
        switch (a.type()) {
            case DELIVER, DELIVER_WITH_PIN -> {
                Optional<ReviewReason> outcome = completeSynced(r, a, viewer, late);
                if (outcome.isPresent() && outcome.get() == ReviewReason.DUPLICATE_DELIVERY) {
                    flag(r, ReviewReason.DUPLICATE_DELIVERY, viewer, now, a.id());
                    return SyncOutcome.rejected("REMITTANCE_ALREADY_COMPLETED",
                            "Remittance " + r.getCode() + " was already completed by someone else");
                }
                flag = outcome.orElse(null);
            }
            case POSTPONE -> {
                requireRecentVisible(r, viewer, late, POSTPONE_PERMISSION);
                postpone(r, a.newDate(), a.reason(), viewer, at);
                flag = null;
            }
            case REPORT_INCIDENT -> {
                requireRecentVisible(r, viewer, late, INCIDENT_PERMISSION);
                reportIncident(r, a.incidentReason(), a.note(), viewer, at);
                flag = null;
            }
            default -> throw new IllegalArgumentException("Unknown action " + a.type());
        }
        if (flag != null) {
            flag(r, flag, viewer, now, a.id());
        }
        syncedActions.save(new SyncedAction(a.id(), viewer.userId(), a.type().name(), r.getId(), at, now,
                blankToNull(a.deviceId())));
        return SyncOutcome.applied(views.of(r, viewer), flag != null);
    }

    /**
     * Completes a remittance from a synced delivery. Returns the review it needs, if any; DUPLICATE_DELIVERY means it
     * was already completed by someone else and nothing was applied.
     */
    private Optional<ReviewReason> completeSynced(Remittance r, SyncAction a, Viewer viewer, boolean late) {
        if (!viewer.has(COURIER_PERMISSION)) {
            throw ApiException.forbidden("FORBIDDEN", "Delivering requires " + COURIER_PERMISSION);
        }
        if (workflow.status(r.getStatus()).isFinalStatus()) {
            if (viewer.userId().equals(r.getCompletedBy())) {
                throw conflict("REMITTANCE_ALREADY_COMPLETED", "Remittance " + r.getCode() + " is already completed");
            }
            return Optional.of(ReviewReason.DUPLICATE_DELIVERY);
        }
        ReviewReason flag = null;
        if (!viewer.canSee(r)) {
            // Reassigned while the phone was offline: the cash already changed hands, so it counts if it was theirs then
            if (!wasAssignedAt(r, viewer.userId(), a.occurredAt())) {
                throw notFound();
            }
            r.assignTo(viewer.userId());
            flag = ReviewReason.DELIVERED_AFTER_REASSIGNMENT;
        }
        if (a.type() == SyncActionType.DELIVER_WITH_PIN && !r.getPin().equals(a.pin())) {
            if (a.strictPin()) {
                throw badRequest("INCORRECT_PIN", "Incorrect PIN");
            }
            flag = flag != null ? flag : ReviewReason.PIN_MISMATCH;
        }
        if (late && flag == null) {
            flag = ReviewReason.LATE_SYNC;
        }
        apply(r, finalTransition(r), null, a.note(), viewer, a.occurredAt());
        return Optional.ofNullable(flag);
    }

    /** Postponing and reporting incidents move no cash: they need the remittance to be theirs now and a recent action. */
    private void requireRecentVisible(Remittance r, Viewer viewer, boolean late, String permission) {
        if (!viewer.has(permission)) {
            throw ApiException.forbidden("FORBIDDEN", "This action requires " + permission);
        }
        if (late) {
            throw badRequest("INVALID_OCCURRED_AT", "Only deliveries can be synced more than 7 days later");
        }
        if (!viewer.canSee(r)) {
            throw notFound();
        }
    }

    private boolean wasAssignedAt(Remittance r, UUID userId, Instant at) {
        return events.findFirstByRemittanceIdAndTypeAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                        r.getId(), EventType.STATUS_CHANGED, at)
                .map(e -> userId.equals(e.getCourierId()))
                .orElse(false);
    }

    private void flag(Remittance r, ReviewReason reason, Viewer viewer, Instant now, UUID actionId) {
        r.flagForReview(reason, now, actionId);
        events.save(RemittanceEvent.reviewFlagged(r, reason, viewer.userId(), now));
    }

    @Override
    @Transactional(readOnly = true)
    public AssignedChanges assignedChangesSince(Instant since, Viewer viewer) {
        if (!viewer.has(Viewer.READ_ASSIGNED)) {
            return new AssignedChanges(since == null, List.of(), List.of());
        }
        List<String> finals = workflow.finalCodes();
        if (since == null || since.isBefore(clock.instant().minus(LATE_SYNC))) {
            return new AssignedChanges(true, assignedTo(viewer), List.of());
        }
        List<Remittance> changed = remittances.findByCourierIdAndStatusNotInAndUpdatedAtAfterOrderByExpectedDateAsc(
                viewer.userId(), finals, since);
        List<UUID> removed = remittances.findNoLongerAssignedSince(viewer.userId(), finals, since);
        return new AssignedChanges(false, views.of(changed, viewer), removed);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesSummary salesSummary(Instant from, Instant to) {
        List<Remittance> registered = remittances.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal deliveredUsd = BigDecimal.ZERO;
        long delivery = 0;
        long completed = 0;
        long withIncident = 0;
        Map<String, CurrencyTotals> byCurrency = new TreeMap<>();
        Map<RemittanceType, TypeTotals> byType = new TreeMap<>();
        for (Remittance r : registered) {
            BigDecimal usd = r.getAmountToDeliver().divide(r.getRate(), 2, RoundingMode.HALF_UP);
            collected = collected.add(r.getTotal());
            fees = fees.add(r.getFee());
            deliveredUsd = deliveredUsd.add(usd);
            delivery += r.getType() == RemittanceType.DELIVERY ? 1 : 0;
            completed += r.getCompletedAt() != null && r.getCompletedAt().isBefore(to) ? 1 : 0;
            withIncident += r.getOpenIncidentReason() != null ? 1 : 0;
            byCurrency.computeIfAbsent(r.getTargetCurrency(), c -> new CurrencyTotals()).add(r, usd);
            byType.computeIfAbsent(r.getType(), t -> new TypeTotals()).add(r);
        }
        long total = registered.size();
        return new SalesSummary(
                new SalesSummary.Counts(total, delivery, total - delivery, completed, total - completed, withIncident),
                collected, fees, deliveredUsd, collected.subtract(deliveredUsd),
                byCurrency.entrySet().stream()
                        .map(e -> new SalesSummary.CurrencyAmount(e.getKey(), e.getValue().delivered)).toList(),
                byCurrency.entrySet().stream().map(e -> e.getValue().view(e.getKey())).toList(),
                byType.entrySet().stream().map(e -> e.getValue().view(e.getKey())).toList());
    }

    /** Running totals of one delivery currency for the sales summary. */
    private static final class CurrencyTotals {
        private long remittances;
        private BigDecimal sent = BigDecimal.ZERO;
        private BigDecimal fees = BigDecimal.ZERO;
        private BigDecimal collected = BigDecimal.ZERO;
        private BigDecimal delivered = BigDecimal.ZERO;
        private BigDecimal deliveredUsd = BigDecimal.ZERO;

        void add(Remittance r, BigDecimal usd) {
            remittances++;
            sent = sent.add(r.getAmount());
            fees = fees.add(r.getFee());
            collected = collected.add(r.getTotal());
            delivered = delivered.add(r.getAmountToDeliver());
            deliveredUsd = deliveredUsd.add(usd);
        }

        SalesSummary.ByCurrency view(String currency) {
            return new SalesSummary.ByCurrency(currency, remittances, sent, fees, collected, delivered, deliveredUsd);
        }
    }

    /** Running totals of one remittance type for the sales summary. */
    private static final class TypeTotals {
        private long remittances;
        private BigDecimal collected = BigDecimal.ZERO;
        private BigDecimal fees = BigDecimal.ZERO;

        void add(Remittance r) {
            remittances++;
            collected = collected.add(r.getTotal());
            fees = fees.add(r.getFee());
        }

        SalesSummary.ByType view(RemittanceType type) {
            return new SalesSummary.ByType(type, remittances, collected, fees);
        }
    }

    @Override
    @Transactional
    public RemittanceView resolveReview(UUID id, String note, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        if (r.getReviewReason() == null) {
            throw conflict("REVIEW_NOT_PENDING", "Remittance " + r.getCode() + " has nothing to review");
        }
        r.resolveReview();
        events.save(RemittanceEvent.reviewResolved(r, viewer.userId(), clock.instant(), note.trim()));
        return views.of(r, viewer);
    }

    @Override
    @Transactional
    public RemittanceView postpone(UUID id, LocalDate newDate, String reason, Viewer viewer) {
        return postpone(findVisible(id, viewer), newDate, reason, viewer, clock.instant());
    }

    /** The new date must be after the current one and not before the day the action happened (business timezone). */
    private RemittanceView postpone(Remittance r, LocalDate newDate, String reason, Viewer viewer, Instant at) {
        if (workflow.status(r.getStatus()).isFinalStatus()) {
            throw conflict("REMITTANCE_ALREADY_COMPLETED", "Remittance " + r.getCode() + " is already completed");
        }
        if (!newDate.isAfter(r.getExpectedDate()) || newDate.isBefore(LocalDate.ofInstant(at, clock.getZone()))) {
            throw badRequest("INVALID_POSTPONE_DATE", "The new date must be after " + r.getExpectedDate() + " and not in the past");
        }
        LocalDate previous = r.getExpectedDate();
        r.postpone(newDate);
        events.save(RemittanceEvent.postponed(r, previous, viewer.userId(), at, reason.trim()));
        return views.of(r, viewer);
    }

    private RemittanceView apply(Remittance r, RemittanceTransition t, UUID courierId, String note, Viewer viewer,
                                 Instant at) {
        if (!viewer.has(t.getPermissionCode())) {
            throw ApiException.forbidden("TRANSITION_FORBIDDEN", "Moving to " + t.getToStatus() + " requires " + t.getPermissionCode());
        }
        RemittanceStatus target = workflow.status(t.getToStatus());
        if (target.isRequiresCourier()) {
            if (courierId == null) {
                throw badRequest("COURIER_REQUIRED", "Status " + target.getCode() + " requires a courier");
            }
            boolean eligible = staff.find(courierId).map(m -> m.can(COURIER_PERMISSION)).orElse(false);
            if (!eligible) {
                throw badRequest("INVALID_COURIER", "The courier must be an active user with permission " + COURIER_PERMISSION);
            }
            r.assignTo(courierId);
        }
        if (target.isFinalStatus()) {
            if (r.getCourierId() == null) {
                throw conflict("COURIER_NOT_ASSIGNED", "Assign a courier before completing " + r.getCode());
            }
            r.complete(at, viewer.userId());
            publisher.publishEvent(new RemittanceCompleted(r.getId(), r.getCode(), r.getType(), r.getCourierId(),
                    r.cashAmount(), r.cashCurrency(), viewer.userId(), at));
        }
        String from = r.getStatus();
        r.moveTo(target.getCode());
        events.save(RemittanceEvent.statusChanged(r, from, viewer.userId(), at, blankToNull(note)));
        return views.of(r, viewer);
    }

    private RemittanceTransition finalTransition(Remittance r) {
        return workflow.firstTransition(r.getStatus(), RemittanceStatus::isFinalStatus)
                .orElseThrow(() -> conflict("REMITTANCE_NOT_COMPLETABLE", "Remittance " + r.getCode() + " cannot be completed in status " + r.getStatus()));
    }

    // ---------------------------------------------------------------- queries

    @Override
    @Transactional(readOnly = true)
    public RemittanceView get(UUID id, Viewer viewer) {
        return views.of(findVisible(id, viewer), viewer);
    }

    @Override
    public Workflow.WorkflowView workflow() {
        return workflow.describe();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RemittanceView> findByIdempotencyKey(String idempotencyKey, Viewer viewer) {
        return remittances.findByIdempotencyKey(idempotencyKey).map(r -> views.of(r, viewer));
    }

    @Override
    @Transactional(readOnly = true)
    public RemittanceView getByCode(String code, Viewer viewer) {
        Remittance r = remittances.findByCode(code.trim().toUpperCase())
                .filter(viewer::canSee)
                .orElseThrow(RemittanceServiceImpl::notFound);
        return views.of(r, viewer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventView> history(UUID id, Viewer viewer) {
        findVisible(id, viewer);
        return events.findByRemittanceIdOrderByOccurredAtAsc(id).stream().map(EventView::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RemittanceView> assignedTo(Viewer viewer) {
        return views.of(remittances.findByCourierIdAndStatusNotInOrderByExpectedDateAsc(viewer.userId(),
                workflow.finalCodes()), viewer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourierView> couriers() {
        Map<UUID, Long> open = new HashMap<>();
        remittances.countOpenByCourier(workflow.finalCodes()).forEach(row -> open.put((UUID) row[0], (Long) row[1]));
        return staff.activeWith(COURIER_PERMISSION).stream()
                .map(m -> new CourierView(m.id(), m.name(), m.email(), open.getOrDefault(m.id(), 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageView search(RemittanceFilter filter, int page, int size, Viewer viewer) {
        Page<Remittance> found = remittances.findAll(specification(filter),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageView(views.of(found.getContent(), viewer), found.getNumber(), found.getSize(),
                found.getTotalElements());
    }

    private Specification<Remittance> specification(RemittanceFilter f) {
        List<String> finals = workflow.finalCodes();
        LocalDate today = today();
        ZoneId zone = clock.getZone();
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (f.status() != null) {
                where.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.type() != null) {
                where.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.courierId() != null) {
                where.add(cb.equal(root.get("courierId"), f.courierId()));
            }
            if (f.customerId() != null) {
                where.add(cb.equal(root.get("customerId"), f.customerId()));
            }
            if (f.beneficiaryId() != null) {
                where.add(cb.equal(root.get("beneficiaryId"), f.beneficiaryId()));
            }
            if (f.municipalityCode() != null) {
                where.add(cb.equal(root.get("beneficiaryMunicipalityCode"), f.municipalityCode()));
            }
            if (f.createdFrom() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.createdFrom().atStartOfDay(zone).toInstant()));
            }
            if (f.createdTo() != null) {
                where.add(cb.lessThan(root.get("createdAt"), f.createdTo().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            if (f.review() != null) {
                where.add(f.review() ? cb.isNotNull(root.get("reviewReason")) : cb.isNull(root.get("reviewReason")));
            }
            if (f.incident() != null) {
                where.add(f.incident() ? cb.isNotNull(root.get("openIncidentReason"))
                        : cb.isNull(root.get("openIncidentReason")));
            }
            if (f.late() != null) {
                Predicate late = cb.and(
                        cb.lessThan(root.get("expectedDate"), today),
                        finals.isEmpty() ? cb.conjunction() : cb.not(root.get("status").in(finals)));
                where.add(f.late() ? late : cb.not(late));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    // ---------------------------------------------------------------- helpers

    private Remittance findVisible(UUID id, Viewer viewer) {
        return remittances.findById(id).filter(viewer::canSee).orElseThrow(RemittanceServiceImpl::notFound);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private String newCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder("OT-");
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (remittances.existsByCode(code));
        return code;
    }

    private static String newPin() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException badRequest(String code, String message) {
        return ApiException.badRequest(code, message);
    }

    private static ApiException conflict(String code, String message) {
        return ApiException.conflict(code, message);
    }

    private static ApiException notFound() {
        return ApiException.notFound("REMITTANCE_NOT_FOUND", "Remittance not found");
    }
}
