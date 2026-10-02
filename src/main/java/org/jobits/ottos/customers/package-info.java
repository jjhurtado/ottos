/**
 * Customers: the people who send remittances, identified by their phone. Kept apart from staff users; for now
 * Sales registers them and they have no login. Self sign-up will add a credentials table here without touching
 * staff accounts. Their history and statistics are computed from their remittances.
 * <p>
 * Public API: {@link org.jobits.ottos.customers.Customers}.
 * Phase 1.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Customers")
package org.jobits.ottos.customers;
