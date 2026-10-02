package org.jobits.ottos.remittances;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published inside the transaction that causes them, so listeners (the cash ledger) commit or roll back with it.
 */
public final class RemittanceEvents {

    private RemittanceEvents() {
    }

    /**
     * A remittance was registered.
     *
     * @param amountCharged what the sender paid when registering it (amount + fee); zero for pickups,
     *                      which are paid through the courier
     */
    public record RemittanceRegistered(UUID remittanceId, String code, RemittanceType type, String currency,
                                       BigDecimal amountCharged, UUID actorId, Instant occurredAt) {
    }

    /**
     * A remittance reached a final status: the courier handed over (DELIVERY) or collected (PICKUP) the cash.
     *
     * @param cashAmount   amount that changed hands
     * @param cashCurrency currency of that amount
     */
    public record RemittanceCompleted(UUID remittanceId, String code, RemittanceType type, UUID courierId,
                                      BigDecimal cashAmount, String cashCurrency, UUID actorId, Instant occurredAt) {
    }
}
