package org.jobits.ottos.remittances;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Imports the business's old records (WhatsApp tickets) as remittances already delivered on their real date, in USD
 * and without a courier (contract: docs/contracts/historical-import.md). Each one records its payment in the
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
     * @param ref              stable id of the ticket in the old records, e.g. chat-20240603-153047
     * @param occurredAt       when it was delivered, Cuba time without zone
     * @param beneficiaryPhone as written in the ticket; matched by its last 8 digits
     * @param beneficiaryName  tells apart beneficiaries sharing a phone
     * @param amountDelivered  USD handed to the beneficiary
     * @param fee              USD charged on top; not more than the amount
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
