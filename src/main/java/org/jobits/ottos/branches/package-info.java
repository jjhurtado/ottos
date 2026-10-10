/**
 * Branches and zones: official provinces and municipalities (only La Habana for now) used for addresses and
 * for assigning remittances by zone. Staff accounts live in {@code identity}; customers in {@code customers}.
 * <p>
 * Public API: {@link org.jobits.ottos.branches.ZoneService}.
 * Phases 0–1.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Branches and zones")
package org.jobits.ottos.branches;
