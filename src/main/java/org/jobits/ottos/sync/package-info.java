/**
 * Mobile sync: what the offline-first mobile app downloads (its open assigned remittances, its cash and the workflow)
 * and the batch endpoint that applies its queue of offline actions. Contract: docs/contracts/offline-sync.md.
 * <p>
 * Public API: {@link org.jobits.ottos.sync.SyncService}. Uses the remittances and payments services.
 * Phase 1.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Mobile sync")
package org.jobits.ottos.sync;
