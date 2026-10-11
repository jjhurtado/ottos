package org.jobits.ottos.identity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only view of staff members for other modules (e.g. to check that someone can act as a courier). */
public interface StaffDirectoryService {

    Optional<StaffMember> find(UUID id);

    /**
     * Staff who can be given work that needs the permission (e.g. couriers): active, holding it, and not
     * administrators. Administrators hold every permission through the built-in role, but are not couriers. By name.
     */
    List<StaffMember> workersWith(String permission);

    /** administrator: holds the built-in ADMIN role (and with it every permission). */
    record StaffMember(UUID id, String name, String email, boolean active, boolean administrator,
                       Set<String> permissions) {

        public boolean can(String permission) {
            return active && permissions.contains(permission);
        }

        /** Can be given work that needs the permission: see {@link StaffDirectoryService#workersWith}. */
        public boolean worksWith(String permission) {
            return can(permission) && !administrator;
        }
    }
}
