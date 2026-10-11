package org.jobits.ottos.identity.security;

import org.jobits.ottos.AuditEvent;
import org.jobits.ottos.identity.domain.Permission;
import org.jobits.ottos.identity.domain.Role;
import org.jobits.ottos.identity.domain.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Publishes an {@link AuditEvent} for every change to staff users and roles. Call it inside the transaction of the
 * change. Passwords never appear, only that one was set.
 */
@Component
public class StaffAudit {

    static final String USER = "USER";
    static final String ROLE = "ROLE";

    private final ApplicationEventPublisher publisher;

    StaffAudit(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void userCreated(User user, UUID actorId) {
        user(user, "USER_CREATED", actorId, details("name", user.getName(), "roles", sorted(user.roleCodes())));
    }

    public void initialAdminCreated(User user) {
        user(user, "USER_CREATED", null, details("name", user.getName(), "roles", sorted(user.roleCodes()),
                "reason", "Initial administrator from OTTOS_ADMIN_EMAIL"));
    }

    public void userRenamed(User user, String previousName, UUID actorId) {
        user(user, "USER_RENAMED", actorId, details("before", previousName, "after", user.getName()));
    }

    public void userRolesChanged(User user, Collection<String> before, UUID actorId) {
        user(user, "USER_ROLES_CHANGED", actorId, details("before", sorted(before), "after", sorted(user.roleCodes())));
    }

    public void userDeactivated(User user, UUID actorId) {
        user(user, "USER_DEACTIVATED", actorId, Map.of());
    }

    public void userActivated(User user, UUID actorId) {
        user(user, "USER_ACTIVATED", actorId, Map.of());
    }

    /** An administrator set someone else's password. */
    public void userPasswordReset(User user, UUID actorId) {
        user(user, "USER_PASSWORD_RESET", actorId, Map.of());
    }

    /** The user changed their own password. */
    public void passwordChanged(User user) {
        user(user, "PASSWORD_CHANGED", user.getId(), Map.of());
    }

    public void roleCreated(Role role, UUID actorId) {
        role(role, "ROLE_CREATED", actorId, details("name", role.getName(), "description", role.getDescription(),
                "permissions", sorted(permissions(role))));
    }

    public void roleDescribed(Role role, String previousName, String previousDescription, UUID actorId) {
        role(role, "ROLE_DESCRIBED", actorId, details(
                "before", details("name", previousName, "description", previousDescription),
                "after", details("name", role.getName(), "description", role.getDescription())));
    }

    public void rolePermissionsChanged(Role role, Collection<String> before, UUID actorId) {
        TreeSet<String> added = new TreeSet<>(permissions(role));
        added.removeAll(before);
        TreeSet<String> removed = new TreeSet<>(before);
        removed.removeAll(permissions(role));
        role(role, "ROLE_PERMISSIONS_CHANGED", actorId, details("added", added, "removed", removed));
    }

    public void roleDeleted(Role role, UUID actorId) {
        role(role, "ROLE_DELETED", actorId, details("name", role.getName(), "permissions", sorted(permissions(role))));
    }

    private void user(User user, String action, UUID actorId, Map<String, Object> details) {
        publisher.publishEvent(new AuditEvent(action, USER, user.getId().toString(), user.getEmail(), actorId,
                Instant.now(), details));
    }

    private void role(Role role, String action, UUID actorId, Map<String, Object> details) {
        publisher.publishEvent(new AuditEvent(action, ROLE, role.getId().toString(), role.getCode(), actorId,
                Instant.now(), details));
    }

    private static Collection<String> permissions(Role role) {
        return role.getPermissions().stream().map(Permission::getCode).toList();
    }

    private static List<String> sorted(Collection<String> values) {
        return values.stream().sorted().toList();
    }

    /** Keeps the order of the pairs and allows null values. */
    private static Map<String, Object> details(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
