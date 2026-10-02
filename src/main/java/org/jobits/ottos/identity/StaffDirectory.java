package org.jobits.ottos.identity;

import org.jobits.ottos.identity.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only view of staff members for other modules (e.g. to check that someone can act as a courier). */
@Service
public class StaffDirectory {

    private final UserRepository users;

    StaffDirectory(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Optional<StaffMember> find(UUID id) {
        return users.findWithRolesById(id)
                .map(u -> new StaffMember(u.getId(), u.getName(), u.getEmail(), u.isActive(), u.permissionCodes()));
    }

    public record StaffMember(UUID id, String name, String email, boolean active, Set<String> permissions) {

        public boolean can(String permission) {
            return active && permissions.contains(permission);
        }
    }
}
