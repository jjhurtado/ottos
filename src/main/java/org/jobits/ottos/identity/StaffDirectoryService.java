package org.jobits.ottos.identity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only view of staff members for other modules (e.g. to check that someone can act as a courier). */
public interface StaffDirectoryService {

    Optional<StaffMember> find(UUID id);

    /** The names of these staff members, by id, in one query (for lists); unknown ids are left out. */
    Map<UUID, String> names(Collection<UUID> ids);

    /** The permission that makes a staff member a courier. */
    String COURIER_PERMISSION = "remittances:deliver";

    /**
     * Couriers, by name: active staff holding {@link #COURIER_PERMISSION} who are not administrators (they hold every
     * permission through the built-in role, but are not couriers). The one rule for listing, assigning and paying them.
     */
    List<StaffMember> couriers();

    /** Whether the user is a courier now, by the rule of {@link #couriers()}. */
    boolean isCourier(UUID id);

    /** administrator: holds the built-in ADMIN role (and with it every permission). */
    record StaffMember(UUID id, String name, String email, boolean active, boolean administrator,
                       Set<String> permissions) {

        public boolean can(String permission) {
            return active && permissions.contains(permission);
        }

        /** See {@link StaffDirectoryService#couriers()}. */
        public boolean isCourier() {
            return can(COURIER_PERMISSION) && !administrator;
        }
    }
}
