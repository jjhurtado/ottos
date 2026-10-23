package org.jobits.ottos.remittances;

import java.util.List;

/**
 * The remittances registered in a period, voided ones included, oldest first (createdAt, then code).
 *
 * @param voided  how many of the lines are voided
 * @param summary the counts of the rest, the same as {@link SalesSummary#remittances()} for that period
 */
public record SalesDetail(long voided, SalesSummary.Counts summary, List<SalesLine> lines) {
}
