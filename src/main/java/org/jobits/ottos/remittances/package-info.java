/**
 * Remittances: deliveries (cash handed to a beneficiary) and pickups (cash collected from a beneficiary), with a
 * tracking code, a copy of the quote and of the beneficiary, an expected date that can be postponed, and the
 * full event history. Statuses and transitions are data (remittance_statuses, remittance_transitions).
 * <p>
 * Public API: {@link org.jobits.ottos.remittances.RemittanceType} and the events in
 * {@link org.jobits.ottos.remittances.RemittanceEvents}, which the cash module listens to.
 * Phase 1.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Remittances")
package org.jobits.ottos.remittances;
