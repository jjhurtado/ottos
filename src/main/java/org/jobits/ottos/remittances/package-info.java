/**
 * Remittances: create a remittance, tracking code, state machine
 * (Created → Paid → Assigned → Out for delivery → Delivered; Issue; Cancelled) and event history.
 * Phase 1.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Remittances")
package org.jobits.ottos.remittances;
