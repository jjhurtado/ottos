package org.jobits.ottos.payments.application;

import org.jobits.ottos.payments.domain.AccountType;
import org.jobits.ottos.payments.domain.MovementType;

import java.math.BigDecimal;
import java.time.Instant;
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
                 UUID remittanceId, String note, UUID actorId, Instant occurredAt) {
    }
}
