package org.jobits.ottos.remittances.application;

import jakarta.persistence.criteria.Predicate;
import org.jobits.ottos.beneficiaries.Beneficiaries;
import org.jobits.ottos.beneficiaries.Beneficiaries.BeneficiaryInfo;
import org.jobits.ottos.customers.Customers;
import org.jobits.ottos.customers.Customers.CustomerInfo;
import org.jobits.ottos.identity.StaffDirectory;
import org.jobits.ottos.rates.Quotes;
import org.jobits.ottos.remittances.RemittanceEvents.RemittanceCompleted;
import org.jobits.ottos.remittances.RemittanceEvents.RemittanceRegistered;
import org.jobits.ottos.remittances.RemittanceType;
import org.jobits.ottos.remittances.domain.Remittance;
import org.jobits.ottos.remittances.domain.RemittanceEvent;
import org.jobits.ottos.remittances.domain.RemittanceEventRepository;
import org.jobits.ottos.remittances.domain.RemittanceRepository;
import org.jobits.ottos.remittances.domain.RemittanceSetting;
import org.jobits.ottos.remittances.domain.RemittanceSettingRepository;
import org.jobits.ottos.remittances.domain.RemittanceStatus;
import org.jobits.ottos.remittances.domain.RemittanceTransition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RemittanceService {

    static final String SOURCE_CURRENCY = "USD";
    private static final String COURIER_PERMISSION = "remittances:deliver";
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RemittanceRepository remittances;
    private final RemittanceEventRepository events;
    private final RemittanceSettingRepository settings;
    private final Workflow workflow;
    private final RemittanceViews views;
    private final Customers customers;
    private final Beneficiaries beneficiaries;
    private final Quotes quotes;
    private final StaffDirectory staff;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    RemittanceService(RemittanceRepository remittances, RemittanceEventRepository events,
                      RemittanceSettingRepository settings, Workflow workflow, RemittanceViews views,
                      Customers customers, Beneficiaries beneficiaries, Quotes quotes, StaffDirectory staff,
                      ApplicationEventPublisher publisher, Clock clock) {
        this.remittances = remittances;
        this.events = events;
        this.settings = settings;
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

    /**
     * Registers a remittance, already paid: the sender pays before it is registered (pickups are paid through
     * the courier). Repeating a request with the same idempotency key returns the first remittance; when two such
     * requests run at the same time, the second fails with DataIntegrityViolationException and the caller looks the
     * first one up with {@link #findByIdempotencyKey}.
     */
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
                .orElseThrow(() -> badRequest("Unknown or inactive customer"));
        BeneficiaryInfo beneficiary = beneficiaries.find(request.beneficiaryId())
                .filter(BeneficiaryInfo::active)
                .orElseThrow(() -> badRequest("Unknown or inactive beneficiary"));
        if (!beneficiaries.isLinked(customer.id(), beneficiary.id())) {
            throw badRequest("The beneficiary is not linked to this customer; link them first");
        }
        Quotes.Quote quote = quotes.quote(SOURCE_CURRENCY, request.targetCurrency(), request.amount());

        Instant now = clock.instant();
        Remittance remittance = new Remittance(
                newCode(), request.type(), workflow.initial().getCode(), customer.id(),
                new Remittance.BeneficiarySnapshot(beneficiary.id(), beneficiary.fullName(), beneficiary.phone(),
                        beneficiary.alternatePhone(), beneficiary.addressLine(), beneficiary.municipalityCode(),
                        beneficiary.reference()),
                new Remittance.QuoteSnapshot(quote.corridor(), quote.sourceCurrency(), quote.targetCurrency(),
                        quote.amount(), quote.fee(), quote.total(), quote.rate(), quote.amountToDeliver(),
                        quote.exchangeRateId(), quote.feeRuleId()),
                newPin(), today().plusDays(defaultDeliveryDays()), blankToNull(request.notes()),
                request.idempotencyKey(), now, viewer.userId());
        remittances.saveAndFlush(remittance);
        events.save(RemittanceEvent.created(remittance, viewer.userId(), now));
        BigDecimal charged = remittance.getType() == RemittanceType.DELIVERY ? remittance.getTotal() : BigDecimal.ZERO;
        publisher.publishEvent(new RemittanceRegistered(remittance.getId(), remittance.getCode(), remittance.getType(),
                remittance.getSourceCurrency(), charged, viewer.userId(), now));
        return views.of(remittance, viewer);
    }

    /** Assigns (or reassigns) a courier through the first allowed transition to a status that requires one. */
    @Transactional
    public RemittanceView assign(UUID id, UUID courierId, String note, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        RemittanceTransition t = workflow.firstTransition(r.getStatus(), RemittanceStatus::isRequiresCourier)
                .orElseThrow(() -> conflict("Remittance " + r.getCode() + " cannot be assigned in status " + r.getStatus()));
        return apply(r, t, courierId, note, viewer);
    }

    /** Marks it delivered (or collected, for pickups) through the allowed transition to a final status. */
    @Transactional
    public RemittanceView complete(UUID id, String note, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        return apply(r, finalTransition(r), null, note, viewer);
    }

    /** Like {@link #complete} but the beneficiary must give the PIN the sender received. */
    @Transactional
    public RemittanceView completeWithPin(UUID id, String pin, String note, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        if (!r.getPin().equals(pin)) {
            throw badRequest("Incorrect PIN");
        }
        return apply(r, finalTransition(r), null, note, viewer);
    }

    /** Moves to any status allowed by remittance_transitions, e.g. a custom status added in the database. */
    @Transactional
    public RemittanceView transition(UUID id, String toStatus, UUID courierId, String note, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        RemittanceTransition t = workflow.transition(r.getStatus(), toStatus)
                .orElseThrow(() -> conflict("Cannot move " + r.getCode() + " from " + r.getStatus() + " to " + toStatus));
        return apply(r, t, courierId, note, viewer);
    }

    /** Moves the expected date later. Allowed for any open remittance; recorded with its reason. */
    @Transactional
    public RemittanceView postpone(UUID id, LocalDate newDate, String reason, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        if (workflow.status(r.getStatus()).isFinalStatus()) {
            throw conflict("Remittance " + r.getCode() + " is already completed");
        }
        if (!newDate.isAfter(r.getExpectedDate()) || newDate.isBefore(today())) {
            throw badRequest("The new date must be after " + r.getExpectedDate() + " and not in the past");
        }
        LocalDate previous = r.getExpectedDate();
        r.postpone(newDate);
        events.save(RemittanceEvent.postponed(r, previous, viewer.userId(), clock.instant(), reason.trim()));
        return views.of(r, viewer);
    }

    private RemittanceView apply(Remittance r, RemittanceTransition t, UUID courierId, String note, Viewer viewer) {
        if (!viewer.has(t.getPermissionCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Moving to " + t.getToStatus() + " requires " + t.getPermissionCode());
        }
        RemittanceStatus target = workflow.status(t.getToStatus());
        if (target.isRequiresCourier()) {
            if (courierId == null) {
                throw badRequest("Status " + target.getCode() + " requires a courier");
            }
            boolean eligible = staff.find(courierId).map(m -> m.can(COURIER_PERMISSION)).orElse(false);
            if (!eligible) {
                throw badRequest("The courier must be an active user with permission " + COURIER_PERMISSION);
            }
            r.assignTo(courierId);
        }
        Instant now = clock.instant();
        if (target.isFinalStatus()) {
            if (r.getCourierId() == null) {
                throw conflict("Assign a courier before completing " + r.getCode());
            }
            r.complete(now, viewer.userId());
            publisher.publishEvent(new RemittanceCompleted(r.getId(), r.getCode(), r.getType(), r.getCourierId(),
                    r.cashAmount(), r.cashCurrency(), viewer.userId(), now));
        }
        String from = r.getStatus();
        r.moveTo(target.getCode());
        events.save(RemittanceEvent.statusChanged(r, from, viewer.userId(), now, blankToNull(note)));
        return views.of(r, viewer);
    }

    private RemittanceTransition finalTransition(Remittance r) {
        return workflow.firstTransition(r.getStatus(), RemittanceStatus::isFinalStatus)
                .orElseThrow(() -> conflict("Remittance " + r.getCode() + " cannot be completed in status " + r.getStatus()));
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public RemittanceView get(UUID id, Viewer viewer) {
        return views.of(findVisible(id, viewer), viewer);
    }

    @Transactional(readOnly = true)
    public Optional<RemittanceView> findByIdempotencyKey(String idempotencyKey, Viewer viewer) {
        return remittances.findByIdempotencyKey(idempotencyKey).map(r -> views.of(r, viewer));
    }

    @Transactional(readOnly = true)
    public RemittanceView getByCode(String code, Viewer viewer) {
        Remittance r = remittances.findByCode(code.trim().toUpperCase())
                .filter(viewer::canSee)
                .orElseThrow(RemittanceService::notFound);
        return views.of(r, viewer);
    }

    @Transactional(readOnly = true)
    public List<EventView> history(UUID id, Viewer viewer) {
        findVisible(id, viewer);
        return events.findByRemittanceIdOrderByOccurredAtAsc(id).stream().map(EventView::of).toList();
    }

    /** Open remittances assigned to the viewer, soonest expected date first. */
    @Transactional(readOnly = true)
    public List<RemittanceView> assignedTo(Viewer viewer) {
        return views.of(remittances.findByCourierIdAndStatusNotInOrderByExpectedDateAsc(viewer.userId(),
                workflow.finalCodes()), viewer);
    }

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
        return remittances.findById(id).filter(viewer::canSee).orElseThrow(RemittanceService::notFound);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private int defaultDeliveryDays() {
        return settings.findById(RemittanceSetting.DEFAULT_DELIVERY_DAYS)
                .map(s -> Integer.parseInt(s.getValue().trim()))
                .orElse(2);
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

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Remittance not found");
    }

    public record NewRemittance(RemittanceType type, UUID customerId, UUID beneficiaryId, BigDecimal amount,
                                String targetCurrency, String notes, String idempotencyKey) {
    }

    public record PageView(List<RemittanceView> items, int page, int size, long totalItems) {
    }

    public record EventView(UUID id, String type, String fromStatus, String toStatus, UUID courierId,
                            LocalDate previousDate, LocalDate newDate, String note, UUID actorId, Instant occurredAt) {

        static EventView of(RemittanceEvent e) {
            return new EventView(e.getId(), e.getType().name(), e.getFromStatus(), e.getToStatus(), e.getCourierId(),
                    e.getPreviousDate(), e.getNewDate(), e.getNote(), e.getActorId(), e.getOccurredAt());
        }
    }
}
