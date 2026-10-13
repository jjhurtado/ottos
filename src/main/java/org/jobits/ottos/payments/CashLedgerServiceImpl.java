package org.jobits.ottos.payments;

import jakarta.persistence.criteria.Predicate;
import org.jobits.ottos.ApiException;
import org.jobits.ottos.Texts;
import org.jobits.ottos.configuration.ConfigurationService;
import org.jobits.ottos.identity.StaffDirectoryService;
import org.jobits.ottos.payments.domain.CashAccount;
import org.jobits.ottos.payments.domain.CashAccountRepository;
import org.jobits.ottos.payments.domain.CashExchange;
import org.jobits.ottos.payments.domain.CashExchangeRepository;
import org.jobits.ottos.payments.domain.CashMovement;
import org.jobits.ottos.payments.domain.CashMovementRepository;
import org.jobits.ottos.remittances.RemittanceEvents.RemittanceCompleted;
import org.jobits.ottos.remittances.RemittanceEvents.RemittanceVoided;
import org.jobits.ottos.remittances.RemittanceType;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Implementation of {@link CashLedgerService}. */
@Service
class CashLedgerServiceImpl implements CashLedgerService {

    private static final int STATEMENT_SIZE = 100;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String UNUSUAL_EXCHANGE_RATE = "UNUSUAL_EXCHANGE_RATE";
    private static final String LATE_SYNC = "LATE_SYNC";
    /** A phone clock may run this far ahead of the server's. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);
    /** Synced exchanges older than this are applied but flagged. */
    private static final Duration LATE = Duration.ofDays(7);

    private final CashAccountRepository accounts;
    private final CashMovementRepository movements;
    private final CashExchangeRepository exchanges;
    private final StaffDirectoryService staff;
    private final ConfigurationService configuration;
    private final Clock clock;

    CashLedgerServiceImpl(CashAccountRepository accounts, CashMovementRepository movements,
                          CashExchangeRepository exchanges, StaffDirectoryService staff,
                          ConfigurationService configuration, Clock clock) {
        this.accounts = accounts;
        this.movements = movements;
        this.exchanges = exchanges;
        this.staff = staff;
        this.configuration = configuration;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- from remittances

    /**
     * The remittance was voided: each movement it caused is undone by its mirror (same amount, accounts swapped), so
     * every cash box is as if it never happened. Voiding twice is not possible, but a second event would add nothing.
     */
    @EventListener
    void on(RemittanceVoided event) {
        List<CashMovement> caused = movements.findByRemittanceIdOrderByOccurredAtAsc(event.remittanceId());
        if (caused.stream().anyMatch(m -> m.getType() == MovementType.REMITTANCE_VOID_REVERSAL)) {
            return;
        }
        for (CashMovement m : caused) {
            CashAccount from = accounts.findById(m.getToAccountId()).orElseThrow();
            CashAccount to = accounts.findById(m.getFromAccountId()).orElseThrow();
            record(MovementType.REMITTANCE_VOID_REVERSAL, from, to, m.getAmount(), event.remittanceId(),
                    "Void of " + event.code(), event.actorId(), event.occurredAt());
        }
    }

    /** The courier handed the cash over (delivery) or collected it (pickup). */
    @EventListener
    void on(RemittanceCompleted event) {
        CashAccount courier = courierAccount(event.courierId(), event.cashCurrency());
        CashAccount outside = external(event.cashCurrency());
        if (event.type() == RemittanceType.DELIVERY) {
            record(MovementType.DELIVERY_PAYOUT, courier, outside, event.cashAmount(), event.remittanceId(),
                    event.code(), event.actorId(), event.occurredAt());
        } else {
            record(MovementType.PICKUP_COLLECTION, outside, courier, event.cashAmount(), event.remittanceId(),
                    event.code(), event.actorId(), event.occurredAt());
        }
    }

    // ---------------------------------------------------------------- by Sales or Admin

    @Override
    @Transactional
    public MovementView fundCourier(UUID courierId, BigDecimal amount, String currency, String note, UUID actorId) {
        requireCourier(courierId);
        CashMovement m = record(MovementType.COURIER_FUNDING, business(currency), courierAccount(courierId, currency),
                amount, null, note, actorId, clock.instant());
        return view(m, null);
    }

    @Override
    @Transactional
    public MovementView courierReturn(UUID courierId, BigDecimal amount, String currency, String note, UUID actorId) {
        requireCourier(courierId);
        CashMovement m = record(MovementType.COURIER_RETURN, courierAccount(courierId, currency), business(currency),
                amount, null, note, actorId, clock.instant());
        return view(m, null);
    }

    @Override
    @Transactional
    public MovementView deposit(BigDecimal amount, String currency, String note, UUID actorId) {
        CashMovement m = record(MovementType.BUSINESS_DEPOSIT, external(currency), business(currency), amount, null,
                note, actorId, clock.instant());
        return view(m, null);
    }

    @Override
    @Transactional
    public MovementView withdraw(BigDecimal amount, String currency, String note, UUID actorId) {
        CashMovement m = record(MovementType.BUSINESS_WITHDRAWAL, business(currency), external(currency), amount, null,
                note, actorId, clock.instant());
        return view(m, null);
    }

    // ---------------------------------------------------------------- currency exchanges

    @Override
    @Transactional
    public ExchangeView recordExchange(UUID courierId, NewExchange exchange, UUID actorId) {
        requireCourier(courierId);
        Instant now = clock.instant();
        return view(exchange(courierId, exchange, now, now, actorId, null, false));
    }

    @Override
    @Transactional
    public SyncedExchange applySyncedExchange(UUID actionId, UUID courierId, NewExchange exchange, Instant occurredAt,
                                              UUID actorId) {
        Optional<CashExchange> done = exchanges.findByClientActionId(actionId);
        if (done.isPresent()) {
            if (!done.get().getCourierId().equals(courierId)) {
                throw ApiException.conflict("ACTION_ID_CONFLICT", "Action " + actionId + " was already used for another action");
            }
            return new SyncedExchange(true, view(done.get()));
        }
        Instant now = clock.instant();
        if (occurredAt.isAfter(now.plus(CLOCK_SKEW))) {
            throw ApiException.badRequest("INVALID_OCCURRED_AT", "occurredAt must not be in the future");
        }
        boolean late = occurredAt.isBefore(now.minus(LATE));
        return new SyncedExchange(false, view(exchange(courierId, exchange, occurredAt, now, actorId, actionId, late)));
    }

    @Override
    @Transactional(readOnly = true)
    public ExchangePage exchanges(UUID courierId, Boolean review, LocalDate from, LocalDate to, int page, int size) {
        ZoneId zone = clock.getZone();
        Specification<CashExchange> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (courierId != null) {
                where.add(cb.equal(root.get("courierId"), courierId));
            }
            if (review != null) {
                Predicate pending = cb.and(cb.isNotNull(root.get("reviewReason")), cb.isNull(root.get("resolvedAt")));
                where.add(review ? pending : cb.not(pending));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from.atStartOfDay(zone).toInstant()));
            }
            if (to != null) {
                where.add(cb.lessThan(root.get("occurredAt"), to.plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Page<CashExchange> found = exchanges.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
        return new ExchangePage(found.getContent().stream().map(this::view).toList(), found.getNumber(),
                found.getSize(), found.getTotalElements());
    }

    @Override
    @Transactional
    public ExchangeView resolveExchangeReview(UUID exchangeId, String note, UUID actorId) {
        CashExchange exchange = exchanges.findById(exchangeId)
                .orElseThrow(() -> ApiException.notFound("EXCHANGE_NOT_FOUND", "Exchange not found"));
        if (!exchange.isPendingReview()) {
            throw ApiException.conflict("REVIEW_NOT_PENDING", "The exchange has nothing to review");
        }
        exchange.resolveReview(clock.instant(), actorId, note.trim());
        return view(exchange);
    }

    /** Validates, computes the amount received, compares the rate with the configured one and records both legs. */
    private CashExchange exchange(UUID courierId, NewExchange e, Instant occurredAt, Instant now, UUID actorId,
                                  UUID actionId, boolean late) {
        String fromCurrency = e.fromCurrency() == null ? null : e.fromCurrency().toUpperCase();
        String toCurrency = e.toCurrency() == null ? null : e.toCurrency().toUpperCase();
        Set<String> handled = configuration.currencies();
        String businessCurrency = configuration.businessCurrency();
        if (fromCurrency == null || toCurrency == null || fromCurrency.equals(toCurrency)
                || !handled.contains(fromCurrency) || !handled.contains(toCurrency)
                || !(businessCurrency.equals(fromCurrency) || businessCurrency.equals(toCurrency))) {
            throw ApiException.badRequest("INVALID_CURRENCY", "Exchange between two different currencies of "
                    + handled + ", one of them " + businessCurrency);
        }
        if (e.fromAmount() == null || e.fromAmount().signum() <= 0 || e.fromAmount().stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Amount must be positive with at most 2 decimals");
        }
        if (e.rate() == null || e.rate().signum() <= 0 || e.rate().stripTrailingZeros().scale() > 6) {
            throw ApiException.badRequest("INVALID_RATE", "Rate must be positive with at most 6 decimals");
        }
        BigDecimal fromAmount = e.fromAmount().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal toAmount = businessCurrency.equals(fromCurrency)
                ? fromAmount.multiply(e.rate()).setScale(2, RoundingMode.HALF_UP)
                : fromAmount.divide(e.rate(), 2, RoundingMode.HALF_UP);
        if (toAmount.signum() <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "The amount is too small to exchange at that rate");
        }
        String other = businessCurrency.equals(fromCurrency) ? toCurrency : fromCurrency;
        BigDecimal configured = configuration.deliveryRateAt(businessCurrency, other, occurredAt).orElse(null);
        BigDecimal deviation = configured == null ? null
                : e.rate().subtract(configured).abs().multiply(HUNDRED).divide(configured, 2, RoundingMode.HALF_UP);
        String note = Texts.blankToNull(e.note());

        CashExchange exchange = exchanges.save(new CashExchange(courierId, fromCurrency, fromAmount, toCurrency,
                toAmount, e.rate(), configured, deviation, note, occurredAt, actorId, now, actionId));
        if (deviation != null && deviation.compareTo(configuration.exchangeRateTolerancePercent()) > 0) {
            exchange.flagForReview(UNUSUAL_EXCHANGE_RATE, now);
        } else if (late) {
            exchange.flagForReview(LATE_SYNC, now);
        }
        movements.save(CashMovement.exchangeLeg(MovementType.COURIER_EXCHANGE_OUT, courierAccount(courierId, fromCurrency),
                external(fromCurrency), fromAmount, exchange.getId(), e.rate(), note, actorId, occurredAt));
        movements.save(CashMovement.exchangeLeg(MovementType.COURIER_EXCHANGE_IN, external(toCurrency),
                courierAccount(courierId, toCurrency), toAmount, exchange.getId(), e.rate(), note, actorId, occurredAt));
        return exchange;
    }

    private ExchangeView view(CashExchange x) {
        String name = staff.find(x.getCourierId()).map(StaffDirectoryService.StaffMember::name).orElse(null);
        ExchangeReview review = x.getReviewReason() == null ? null : new ExchangeReview(x.getReviewReason(),
                x.getReviewFlaggedAt(), x.getResolvedAt(), x.getResolvedBy(), x.getResolutionNote());
        return new ExchangeView(x.getId(), x.getCourierId(), name, x.getFromCurrency(), x.getFromAmount(),
                x.getToCurrency(), x.getToAmount(), x.getRate(), x.getConfiguredRate(), x.getDeviationPercent(),
                x.getNote(), x.getOccurredAt(), x.getRecordedBy(), review);
    }

    // ---------------------------------------------------------------- queries

    @Override
    @Transactional(readOnly = true)
    public List<Balance> businessBalances() {
        return balances(accounts.findByTypeOrderByCurrency(AccountType.BUSINESS));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourierCash> couriers() {
        Map<UUID, List<CashAccount>> byCourier = accounts.findByTypeOrderByCurrency(AccountType.COURIER).stream()
                .collect(Collectors.groupingBy(CashAccount::getOwnerId, LinkedHashMap::new, Collectors.toList()));
        // Every current courier, also one without movements yet (to give them their first funds), plus anyone who
        // still has courier cash although they no longer are one (deactivated). Administrators are not couriers.
        staff.couriers().forEach(m -> byCourier.putIfAbsent(m.id(), List.of()));
        return byCourier.entrySet().stream()
                .filter(e -> staff.find(e.getKey()).map(m -> !m.administrator()).orElse(true))
                .map(e -> courierCash(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CourierCash::name, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CourierStatement courierStatement(UUID courierId) {
        List<CashAccount> own = accounts.findByTypeAndOwnerIdOrderByCurrency(AccountType.COURIER, courierId);
        List<UUID> ids = own.stream().map(CashAccount::getId).toList();
        List<MovementView> latest = ids.isEmpty() ? List.of()
                : movements.findTouching(ids, PageRequest.of(0, STATEMENT_SIZE)).stream()
                        .map(m -> view(m, ids.contains(m.getToAccountId()) ? m.getToAccountId() : m.getFromAccountId()))
                        .toList();
        return new CourierStatement(courierCash(courierId, own), latest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovementView> businessMovements() {
        List<UUID> ids = accounts.findByTypeOrderByCurrency(AccountType.BUSINESS).stream().map(CashAccount::getId).toList();
        return movements.findTouching(ids, PageRequest.of(0, STATEMENT_SIZE)).stream()
                .map(m -> view(m, ids.contains(m.getToAccountId()) ? m.getToAccountId() : m.getFromAccountId()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovementView> movementsOfRemittance(UUID remittanceId) {
        return movements.findByRemittanceIdOrderByOccurredAtAsc(remittanceId).stream().map(m -> view(m, null)).toList();
    }

    // ---------------------------------------------------------------- helpers

    private CashMovement record(MovementType type, CashAccount from, CashAccount to, BigDecimal amount,
                                UUID remittanceId, String note, UUID actorId, Instant at) {
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Amount must be positive");
        }
        return movements.save(new CashMovement(type, from, to, amount, remittanceId, note, actorId, at));
    }

    /** The business box of a currency it handles (one of the active corridors), created the first time it is used. */
    private CashAccount business(String currency) {
        if (!configuration.currencies().contains(currency)) {
            throw ApiException.badRequest("NO_BUSINESS_CASH", "The business has no " + currency + " cash");
        }
        return findOrCreate(AccountType.BUSINESS, null, currency);
    }

    private CashAccount external(String currency) {
        return findOrCreate(AccountType.EXTERNAL, null, currency);
    }

    private CashAccount courierAccount(UUID courierId, String currency) {
        return findOrCreate(AccountType.COURIER, courierId, currency);
    }

    private CashAccount findOrCreate(AccountType type, UUID ownerId, String currency) {
        String key = CashAccount.keyOf(type, ownerId, currency);
        return accounts.findByKey(key).orElseGet(() -> {
            accounts.insertIfAbsent(UUID.randomUUID(), key, type.name(), ownerId, currency, clock.instant());
            return accounts.findByKey(key).orElseThrow();
        });
    }

    private void requireCourier(UUID courierId) {
        if (!staff.isCourier(courierId)) {
            throw ApiException.badRequest("INVALID_COURIER",
                    "The courier must be an active user with permission " + StaffDirectoryService.COURIER_PERMISSION + ", not an administrator");
        }
    }

    private List<Balance> balances(List<CashAccount> list) {
        return list.stream()
                .map(a -> new Balance(a.getCurrency(), movements.balanceOf(a.getId()).setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    private CourierCash courierCash(UUID courierId, List<CashAccount> own) {
        List<Balance> balances = balances(own);
        String name = staff.find(courierId).map(StaffDirectoryService.StaffMember::name).orElse(null);
        boolean negative = balances.stream().anyMatch(b -> b.amount().signum() < 0);
        return new CourierCash(courierId, name, balances, negative);
    }

    private MovementView view(CashMovement m, UUID perspective) {
        Map<UUID, CashAccount> involved = accounts.findAllById(List.of(m.getFromAccountId(), m.getToAccountId()))
                .stream().collect(Collectors.toMap(CashAccount::getId, Function.identity()));
        CashAccount from = involved.get(m.getFromAccountId());
        CashAccount to = involved.get(m.getToAccountId());
        BigDecimal signed = perspective == null ? null
                : perspective.equals(m.getToAccountId()) ? m.getAmount() : m.getAmount().negate();
        return new MovementView(m.getId(), m.getType(), from.getType(), from.getOwnerId(), to.getType(), to.getOwnerId(),
                m.getAmount(), m.getCurrency(), signed, m.getRemittanceId(), m.getExchangeId(), m.getRate(), m.getNote(),
                m.getActorId(), m.getOccurredAt());
    }
}
