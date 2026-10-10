package org.jobits.ottos.remittances.application;

import jakarta.persistence.criteria.Predicate;
import org.jobits.ottos.ApiException;
import org.jobits.ottos.beneficiaries.BeneficiaryService;
import org.jobits.ottos.beneficiaries.BeneficiaryService.BeneficiaryInfo;
import org.jobits.ottos.configuration.ConfigurationService;
import org.jobits.ottos.customers.CustomerService;
import org.jobits.ottos.customers.CustomerService.CustomerInfo;
import org.jobits.ottos.identity.StaffDirectoryService;
import org.jobits.ottos.rates.QuoteService;
import org.jobits.ottos.remittances.RemittanceEvents.RemittanceCompleted;
import org.jobits.ottos.remittances.RemittanceEvents.RemittanceRegistered;
import org.jobits.ottos.remittances.IncidentReason;
import org.jobits.ottos.remittances.RemittanceType;
import org.jobits.ottos.remittances.domain.Remittance;
import org.jobits.ottos.remittances.domain.RemittanceEvent;
import org.jobits.ottos.remittances.domain.RemittanceEventRepository;
import org.jobits.ottos.remittances.domain.RemittanceRepository;
import org.jobits.ottos.remittances.domain.RemittanceStatus;
import org.jobits.ottos.remittances.domain.RemittanceTransition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Implementation of {@link RemittanceService}. */
@Service
class RemittanceServiceImpl implements RemittanceService {

    static final String SOURCE_CURRENCY = "USD";
    private static final String COURIER_PERMISSION = "remittances:deliver";
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RemittanceRepository remittances;
    private final RemittanceEventRepository events;
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
                      ConfigurationService configuration, Workflow workflow, RemittanceViews views,
                      CustomerService customers, BeneficiaryService beneficiaries, QuoteService quotes, StaffDirectoryService staff,
                      ApplicationEventPublisher publisher, Clock clock) {
        this.remittances = remittances;
        this.events = events;
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
        if (!beneficiaries.isLinked(customer.id(), beneficiary.id())) {
            throw badRequest("BENEFICIARY_NOT_LINKED", "The beneficiary is not linked to this customer; link them first");
        }
        QuoteService.Quote quote = quotes.quote(SOURCE_CURRENCY, request.targetCurrency(), request.amount());

        Instant now = clock.instant();
        Remittance remittance = new Remittance(
                newCode(), request.type(), workflow.initial().getCode(), customer.id(),
                new Remittance.BeneficiarySnapshot(beneficiary.id(), beneficiary.fullName(), beneficiary.phone(),
                        beneficiary.alternatePhone(), beneficiary.addressLine(), beneficiary.municipalityCode(),
                        beneficiary.reference()),
                new Remittance.QuoteSnapshot(quote.corridor(), quote.sourceCurrency(), quote.targetCurrency(),
                        quote.amount(), quote.fee(), quote.total(), quote.rate(), quote.amountToDeliver(),
                        quote.exchangeRateId(), quote.feeRuleId()),
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
        return apply(r, t, courierId, note, viewer);
    }

    @Override
    @Transactional
    public RemittanceView complete(UUID id, String note, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        return apply(r, finalTransition(r), null, note, viewer);
    }

    @Override
    @Transactional
    public RemittanceView completeWithPin(UUID id, String pin, String note, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        if (!r.getPin().equals(pin)) {
            throw badRequest("INCORRECT_PIN", "Incorrect PIN");
        }
        return apply(r, finalTransition(r), null, note, viewer);
    }

    @Override
    @Transactional
    public RemittanceView transition(UUID id, String toStatus, UUID courierId, String note, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        RemittanceTransition t = workflow.transition(r.getStatus(), toStatus)
                .orElseThrow(() -> conflict("TRANSITION_NOT_ALLOWED", "Cannot move " + r.getCode() + " from " + r.getStatus() + " to " + toStatus));
        return apply(r, t, courierId, note, viewer);
    }

    @Override
    @Transactional
    public RemittanceView reportIncident(UUID id, IncidentReason reason, String note, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
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
        Instant now = clock.instant();
        r.reportIncident(reason, cleanNote, now, viewer.userId());
        events.save(RemittanceEvent.incidentReported(r, reason, viewer.userId(), now, cleanNote));
        return views.of(r, viewer);
    }

    @Override
    @Transactional
    public RemittanceView postpone(UUID id, LocalDate newDate, String reason, Viewer viewer) {
        Remittance r = findVisible(id, viewer);
        if (workflow.status(r.getStatus()).isFinalStatus()) {
            throw conflict("REMITTANCE_ALREADY_COMPLETED", "Remittance " + r.getCode() + " is already completed");
        }
        if (!newDate.isAfter(r.getExpectedDate()) || newDate.isBefore(today())) {
            throw badRequest("INVALID_POSTPONE_DATE", "The new date must be after " + r.getExpectedDate() + " and not in the past");
        }
        LocalDate previous = r.getExpectedDate();
        r.postpone(newDate);
        events.save(RemittanceEvent.postponed(r, previous, viewer.userId(), clock.instant(), reason.trim()));
        return views.of(r, viewer);
    }

    private RemittanceView apply(Remittance r, RemittanceTransition t, UUID courierId, String note, Viewer viewer) {
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
        Instant now = clock.instant();
        if (target.isFinalStatus()) {
            if (r.getCourierId() == null) {
                throw conflict("COURIER_NOT_ASSIGNED", "Assign a courier before completing " + r.getCode());
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
