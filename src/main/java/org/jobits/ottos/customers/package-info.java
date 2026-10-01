/**
 * Customers: the people who send remittances. Kept apart from staff users; for now Sales registers them and
 * they have no login. Self sign-up will add a credentials table here without touching staff accounts.
 * Phase 1.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Customers")
package org.jobits.ottos.customers;
