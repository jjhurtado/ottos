package org.jobits.ottos.remittances;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Imports the business's old records (WhatsApp tickets) as remittances already delivered on their real date, in the
 * business currency and without a courier (contract: docs/contracts/historical-import.md). Each one records its payment in the
 * business cash on that date, like any registration, and no courier's cash moves. Every batch is audited, so using it
 * again later leaves a trail and shows in the cash.
 */
public interface HistoricalImportService {

    /**
     * Imports each row in its own transaction; a rejected row does not stop the rest, and importing a ref again is
     * harmless (EXISTING). With dryRun, the same result is computed and nothing is saved.
     *
     * @param customerId the customer every row is attributed to (the tickets do not say who sent the money: a
     *                   placeholder customer created from the panel)
     */
    ImportResult importHistorical(boolean dryRun, UUID customerId, List<HistoricalRow> rows, UUID actorId);

    /**
     * Registers one old remittance or pickup by hand, completed on its date, with no courier and moving no cash.
     * Audited (HISTORICAL_REMITTANCE_CREATED). With a ref, repeating it returns the first one (created = false).
     */
    Created create(NewHistorical remittance, Viewer viewer);

    /** created: false when the ref had already been registered and the existing remittance is returned. */
    record Created(RemittanceView remittance, boolean created) {
    }

    /**
     * @param targetCurrency  a currency of an active corridor (default: the business currency); a pickup only in the
     *                        business currency
     * @param amount          sent (delivery) or collected (pickup), in the business currency
     * @param fee             in the business currency, ≥ 0
     * @param rate            target units per unit of the business currency; required for another currency
     * @param amountToDeliver handed over in the target currency, as on the ticket; required for another currency
     * @param ref             optional id of the ticket in the old records; registering the same ref again is harmless
     */
    record NewHistorical(String ref, RemittanceType type, UUID customerId, UUID beneficiaryId, LocalDateTime occurredAt,
                         String targetCurrency, BigDecimal amount, BigDecimal fee, BigDecimal rate,
                         BigDecimal amountToDeliver, String notes) {
    }

    /**
     * @param ref              stable id of the ticket in the old records, e.g. chat-20240603-153047
     * @param occurredAt       when it was delivered, Cuba time without zone
     * @param beneficiaryPhone as written in the ticket; matched by its last 8 digits
     * @param beneficiaryName  tells apart beneficiaries sharing a phone
     * @param amountDelivered  handed to the beneficiary, in the business currency
     * @param fee              charged on top, in the business currency; not more than the amount
     */
    record HistoricalRow(String ref, LocalDateTime occurredAt, String beneficiaryPhone, String beneficiaryName,
                         BigDecimal amountDelivered, BigDecimal fee) {
    }

    enum RowStatus {
        CREATED,
        EXISTING,
        REJECTED
    }

    record ImportResult(boolean dryRun, Summary summary, List<RowResult> results) {
    }

    record Summary(long created, long existing, long rejected) {
    }

    /** remittanceId is null when rejected, or in a dry run for a row that would be created. */
    record RowResult(int index, String ref, RowStatus status, UUID remittanceId, String code, String message) {
    }
}
