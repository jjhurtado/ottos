package org.jobits.ottos.remittances;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The remittance side of the mobile app's offline mode, used by the sync module (contract: docs/contracts/offline-sync.md). */
public interface RemittanceSyncService {

    /**
     * Applies one action of the mobile app's offline queue, at the moment it happened (occurredAt), in its own
     * transaction. Sending an action again returns ALREADY_APPLIED. Rejections are thrown as ApiException with the
     * same codes as the online endpoints, plus INVALID_OCCURRED_AT and ACTION_ID_CONFLICT. See
     * docs/contracts/offline-sync.md for the conflict and review rules.
     */
    SyncOutcome applySynced(SyncAction action, Viewer viewer);

    /**
     * Open remittances assigned to the viewer that changed after since, and the ones to remove from the phone; all of
     * them (full) without since or when since is more than 7 days old. Empty without remittances:read-assigned.
     */
    AssignedChanges assignedChangesSince(Instant since, Viewer viewer);

    record AssignedChanges(boolean full, List<RemittanceView> remittances, List<UUID> removedIds) {
    }
}
