package org.jobits.ottos.identity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only view of staff members for other modules (e.g. to check that someone can act as a courier). */
public interface StaffDirectoryService {

    Optional<StaffMember> find(UUID id);

    /** Active staff members holding the permission through any of their roles, by name. */
    List<StaffMember> activeWith(String permission);

    record StaffMember(UUID id, String name, String email, boolean active, Set<String> permissions) {

        public boolean can(String permission) {
            return active && permissions.contains(permission);
        }
    }
}
