package org.jobits.ottos.identity.management;

import org.jobits.ottos.identity.domain.Permission;
import org.jobits.ottos.identity.domain.Role;
import org.jobits.ottos.identity.domain.User;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Read models returned by the management services, built inside their transactions. */
public final class Views {

    private Views() {
    }

    public record PermissionView(String code, String module, String description) {

        static PermissionView of(Permission p) {
            return new PermissionView(p.getCode(), p.getModule(), p.getDescription());
        }
    }

    public record RoleView(UUID id, String code, String name, String description, boolean builtIn,
                           List<String> permissions, Instant createdAt) {

        static RoleView of(Role r) {
            List<String> permissions = r.getPermissions().stream().map(Permission::getCode).sorted().toList();
            return new RoleView(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isBuiltIn(),
                    permissions, r.getCreatedAt());
        }
    }

    public record RoleSummary(UUID id, String code, String name) {

        static RoleSummary of(Role r) {
            return new RoleSummary(r.getId(), r.getCode(), r.getName());
        }
    }

    public record UserView(UUID id, String email, String name, boolean active, List<RoleSummary> roles,
                           Instant createdAt) {

        static UserView of(User u) {
            List<RoleSummary> roles = u.getRoles().stream()
                    .sorted(Comparator.comparing(Role::getCode))
                    .map(RoleSummary::of)
                    .toList();
            return new UserView(u.getId(), u.getEmail(), u.getName(), u.isActive(), roles, u.getCreatedAt());
        }
    }
}
