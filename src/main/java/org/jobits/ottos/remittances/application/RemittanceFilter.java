package org.jobits.ottos.remittances.application;

import org.jobits.ottos.remittances.RemittanceType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional filters for listing remittances; null means "any".
 *
 * @param late        true: not completed and the expected date has passed; false: the opposite
 * @param createdFrom first day (inclusive, business timezone)
 * @param createdTo   last day (inclusive, business timezone)
 */
public record RemittanceFilter(String status, RemittanceType type, Boolean late, UUID courierId, UUID customerId,
                               UUID beneficiaryId, String municipalityCode, LocalDate createdFrom,
                               LocalDate createdTo) {
}
