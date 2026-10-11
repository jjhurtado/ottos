package org.jobits.ottos.payments.application;

import org.jobits.ottos.payments.domain.AccountType;
import org.jobits.ottos.payments.domain.MovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Records cash movements and computes balances. Remittance payments, deliveries and pickups are recorded from
 * remittance events, inside the same transaction; funding and returns are recorded by Sales or Admin.
 */
public interface CashLedgerService {

    /** Cash handed from the business box to a courier. */
    MovementView fundCourier(UUID courierId, BigDecimal amount, String currency, String note, UUID actorId);

    /** Cash a courier gave back to the business box. */
    MovementView courierReturn(UUID courierId, BigDecimal amount, String currency, String note, UUID actorId);

    /** Money put into the business box for a reason other than a remittance; the note says why. */
    MovementView deposit(BigDecimal amount, String currency, String note, UUID actorId);

    /** Money taken out of the business box for a reason other than a remittance; the note says why. */
    MovementView withdraw(BigDecimal amount, String currency, String note, UUID actorId);

    List<Balance> businessBalances();

    /** Every courier that has ever carried cash, with their balance per currency. */
    List<CourierCash> couriers();

    /** A courier's balances and their latest movements, signed from the courier's point of view. */
    CourierStatement courierStatement(UUID courierId);

    /** Latest movements of the business box, signed from the business's point of view. */
    List<MovementView> businessMovements();

    List<MovementView> movementsOfRemittance(UUID remittanceId);

    /**
     * A courier changes cash from one currency to another at the rate they got (units of the other currency per 1 USD;
     * one of the two currencies is USD). Two movements inside the courier's cash; flagged for review when the rate
     * strays from the configured delivery rate more than the configured tolerance.
     */
    ExchangeView recordExchange(UUID courierId, NewExchange exchange, UUID actorId);

    /**
     * Same, from the mobile app's offline queue, at the moment it happened. The phone's action id makes sending it
     * again harmless; exchanges older than 7 days are applied but flagged LATE_SYNC.
     */
    SyncedExchange applySyncedExchange(UUID actionId, UUID courierId, NewExchange exchange, Instant occurredAt,
                                       UUID actorId);

    /** Exchanges, newest first, optionally of one courier, pending review (review=true) or within dates. */
    ExchangePage exchanges(UUID courierId, Boolean review, LocalDate from, LocalDate to, int page, int size);

    /** Clears the pending review of an exchange with a note. */
    ExchangeView resolveExchangeReview(UUID exchangeId, String note, UUID actorId);

    record Balance(String currency, BigDecimal amount) {
    }

    /** negative: the courier has handed over more than they received in some currency. */
    record CourierCash(UUID courierId, String name, List<Balance> balances, boolean negative) {
    }

    record CourierStatement(CourierCash cash, List<MovementView> movements) {
    }

    /** signedAmount: positive if the account being viewed received the money, negative if it paid; null otherwise. */
    record MovementView(UUID id, MovementType type, AccountType fromType, UUID fromOwnerId, AccountType toType,
                 UUID toOwnerId, BigDecimal amount, String currency, BigDecimal signedAmount,
                 UUID remittanceId, UUID exchangeId, BigDecimal rate, String note, UUID actorId, Instant occurredAt) {
    }

    /** rate: units of the other currency per 1 USD, in both directions (480 = 480 CUP per 1 USD). */
    record NewExchange(String fromCurrency, BigDecimal fromAmount, String toCurrency, BigDecimal rate, String note) {
    }

    /**
     * @param configuredRate   the configured delivery rate it was compared with; null if none was set
     * @param deviationPercent how far the rate strayed from it, in percent
     * @param review           null if it was never flagged
     */
    record ExchangeView(UUID id, UUID courierId, String courierName, String fromCurrency, BigDecimal fromAmount,
                        String toCurrency, BigDecimal toAmount, BigDecimal rate, BigDecimal configuredRate,
                        BigDecimal deviationPercent, String note, Instant occurredAt, UUID recordedBy,
                        ExchangeReview review) {
    }

    /** Pending while resolvedAt is null. reason: UNUSUAL_EXCHANGE_RATE or LATE_SYNC. */
    record ExchangeReview(String reason, Instant flaggedAt, Instant resolvedAt, UUID resolvedBy, String resolvedNote) {
    }

    record SyncedExchange(boolean alreadyApplied, ExchangeView exchange) {
    }

    record ExchangePage(List<ExchangeView> items, int page, int size, long totalItems) {
    }
}
