package org.jobits.ottos.payments.application;

import org.jobits.ottos.identity.StaffDirectory;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

/**
 * Records cash movements and computes balances. Remittance payments, deliveries and pickups are recorded from
 * remittance events, inside the same transaction; funding and returns are recorded by Sales or Admin.
 */
@Service
public class CashLedger {

    private static final String COURIER_PERMISSION = "remittances:deliver";
    private static final int STATEMENT_SIZE = 100;

    private final CashAccountRepository accounts;
    private final CashMovementRepository movements;
    private final StaffDirectory staff;
    private final Clock clock;

    CashLedger(CashAccountRepository accounts, CashMovementRepository movements, StaffDirectory staff, Clock clock) {
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

    /** Cash handed from the business box to a courier. */
    @Transactional
    public MovementView fundCourier(UUID courierId, BigDecimal amount, String currency, String note, UUID actorId) {
        requireCourier(courierId);
        CashMovement m = record(MovementType.COURIER_FUNDING, business(currency), courierAccount(courierId, currency),
                amount, null, note, actorId, clock.instant());
        return view(m, null);
    }

    /** Cash a courier gave back to the business box. */
    @Transactional
    public MovementView courierReturn(UUID courierId, BigDecimal amount, String currency, String note, UUID actorId) {
        requireCourier(courierId);
        CashMovement m = record(MovementType.COURIER_RETURN, courierAccount(courierId, currency), business(currency),
                amount, null, note, actorId, clock.instant());
        return view(m, null);
    }

    /** Money put into the business box for a reason other than a remittance; the note says why. */
    @Transactional
    public MovementView deposit(BigDecimal amount, String currency, String note, UUID actorId) {
        CashMovement m = record(MovementType.BUSINESS_DEPOSIT, external(currency), business(currency), amount, null,
                note, actorId, clock.instant());
        return view(m, null);
    }

    /** Money taken out of the business box for a reason other than a remittance; the note says why. */
    @Transactional
    public MovementView withdraw(BigDecimal amount, String currency, String note, UUID actorId) {
        CashMovement m = record(MovementType.BUSINESS_WITHDRAWAL, business(currency), external(currency), amount, null,
                note, actorId, clock.instant());
        return view(m, null);
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<Balance> businessBalances() {
        return balances(accounts.findByTypeOrderByCurrency(AccountType.BUSINESS));
    }

    /** Every courier that has ever carried cash, with their balance per currency. */
    @Transactional(readOnly = true)
    public List<CourierCash> couriers() {
        Map<UUID, List<CashAccount>> byCourier = accounts.findByTypeOrderByCurrency(AccountType.COURIER).stream()
                .collect(Collectors.groupingBy(CashAccount::getOwnerId));
        return byCourier.entrySet().stream()
                .map(e -> courierCash(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CourierCash::name, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** A courier's balances and their latest movements, signed from the courier's point of view. */
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

    /** Latest movements of the business box, signed from the business's point of view. */
    @Transactional(readOnly = true)
    public List<MovementView> businessMovements() {
        List<UUID> ids = accounts.findByTypeOrderByCurrency(AccountType.BUSINESS).stream().map(CashAccount::getId).toList();
        return movements.findTouching(ids, PageRequest.of(0, STATEMENT_SIZE)).stream()
                .map(m -> view(m, ids.contains(m.getToAccountId()) ? m.getToAccountId() : m.getFromAccountId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovementView> movementsOfRemittance(UUID remittanceId) {
        return movements.findByRemittanceIdOrderByOccurredAtAsc(remittanceId).stream().map(m -> view(m, null)).toList();
    }

    // ---------------------------------------------------------------- helpers

    private CashMovement record(MovementType type, CashAccount from, CashAccount to, BigDecimal amount,
                                UUID remittanceId, String note, UUID actorId, Instant at) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
        }
        return movements.save(new CashMovement(type, from, to, amount, remittanceId, note, actorId, at));
    }

    private CashAccount business(String currency) {
        return accounts.findByKey(CashAccount.keyOf(AccountType.BUSINESS, null, currency))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "The business has no " + currency + " cash"));
    }

    private CashAccount external(String currency) {
        return findOrCreate(AccountType.EXTERNAL, null, currency);
    }

    private CashAccount courierAccount(UUID courierId, String currency) {
        return findOrCreate(AccountType.COURIER, courierId, currency);
    }

    private CashAccount findOrCreate(AccountType type, UUID ownerId, String currency) {
        return accounts.findByKey(CashAccount.keyOf(type, ownerId, currency))
                .orElseGet(() -> accounts.save(new CashAccount(type, ownerId, currency, clock.instant())));
    }

    private void requireCourier(UUID courierId) {
        boolean eligible = staff.find(courierId).map(m -> m.can(COURIER_PERMISSION)).orElse(false);
        if (!eligible) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
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
        String name = staff.find(courierId).map(StaffDirectory.StaffMember::name).orElse(null);
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

    public record Balance(String currency, BigDecimal amount) {
    }

    /** negative: the courier has handed over more than they received in some currency. */
    public record CourierCash(UUID courierId, String name, List<Balance> balances, boolean negative) {
    }

    public record CourierStatement(CourierCash cash, List<MovementView> movements) {
    }

    /** signedAmount: positive if the account being viewed received the money, negative if it paid; null otherwise. */
    public record MovementView(UUID id, MovementType type, AccountType fromType, UUID fromOwnerId, AccountType toType,
                               UUID toOwnerId, BigDecimal amount, String currency, BigDecimal signedAmount,
                               UUID remittanceId, String note, UUID actorId, Instant occurredAt) {
    }
}
