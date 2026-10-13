package org.jobits.ottos.remittances;


import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional filters for listing remittances; null means "any".
 *
 * @param late        true: not completed and the expected date has passed; false: the opposite
 * @param incident    true: with an open incident; false: without one
 * @param review      true: pending review; false: nothing to review
 * @param voided      true: only the voided ones; false: leave them out
 * @param createdFrom first day (inclusive, business timezone)
 * @param createdTo   last day (inclusive, business timezone)
 */
public record RemittanceFilter(String status, RemittanceType type, Boolean late, Boolean incident, Boolean review,
                               Boolean voided,
                               UUID courierId, UUID customerId, UUID beneficiaryId, String municipalityCode,
                               LocalDate createdFrom, LocalDate createdTo) {
}
