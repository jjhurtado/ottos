/**
 * Payments and cash: a double-entry ledger of the business cash box and of the cash each courier carries.
 * Remittance payments, deliveries and pickups are recorded automatically from remittance events; Sales or Admin
 * record the cash handed to couriers and the cash they return. A courier's balance may go negative.
 * Phases 1 and 3 (closing and reconciliation).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payments and cash")
package org.jobits.ottos.payments;
