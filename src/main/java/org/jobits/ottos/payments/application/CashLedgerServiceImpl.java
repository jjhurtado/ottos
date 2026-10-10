package org.jobits.ottos.payments.application;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.identity.StaffDirectoryService;
import org.jobits.ottos.payments.domain.AccountType;
import org.jobits.ottos.payments.domain.CashAccount;
import org.jobits.ottos.payments.domain.CashAccountRepository;
import org.jobits.ottos.payments.domain.CashMovement;
import org.jobits.ottos.payments.domain.CashMovementRepository;
import org.jobits.ottos.payments.domain.MovementType;
import org.jobits.ottos.remittances.RemittanceEvents.RemittanceCompleted;
import org.jobits.ottos.remittances.RemittanceEvents.RemittanceRegistered;
import org.jobits.ottos.remittances.RemittanceType;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Implementation of {@link CashLedgerService}. */
@Service
class CashLedgerServiceImpl implements CashLedgerService {

    private static final String COURIER_PERMISSION = "remittances:deliver";
    private static final int STATEMENT_SIZE = 100;

    private final CashAccountRepository accounts;
    private final CashMovementRepository movements;
    private final StaffDirectoryService staff;
    private final Clock clock;

    CashLedgerServiceImpl(CashAccountRepository accounts, CashMovementRepository movements, StaffDirectoryService staff, Clock clock) {
        this.accounts = accounts;
        this.movements = movements;
        this.staff = staff;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- from remittances

    /** The sender paid the remittance (amount + fee) to the business. */
    @EventListener
    void on(RemittanceRegistered event) {
        if (event.amountCharged().signum() > 0) {
            record(MovementType.REMITTANCE_PAYMENT, external(event.currency()), business(event.currency()),
                    event.amountCharged(), event.remittanceId(), event.code(), event.actorId(), event.occurredAt());
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
                .collect(Collectors.groupingBy(CashAccount::getOwnerId));
        return byCourier.entrySet().stream()
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

    private CashAccount business(String currency) {
        return accounts.findByKey(CashAccount.keyOf(AccountType.BUSINESS, null, currency))
                .orElseThrow(() -> ApiException.badRequest("NO_BUSINESS_CASH", "The business has no " + currency + " cash"));
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
        boolean eligible = staff.find(courierId).map(m -> m.can(COURIER_PERMISSION)).orElse(false);
        if (!eligible) {
            throw ApiException.badRequest("INVALID_COURIER",
                    "The courier must be an active user with permission " + COURIER_PERMISSION);
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
                m.getAmount(), m.getCurrency(), signed, m.getRemittanceId(), m.getNote(), m.getActorId(), m.getOccurredAt());
    }
}
