package org.jobits.ottos.identity.management;

import org.jobits.ottos.ApiException;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The authenticated staff member performing an administrative action.
 * Nobody can hand out permissions they don't hold themselves, either by editing a role or by assigning one.
 */
public record Actor(UUID userId, Set<String> permissions) {

    public Actor {
        permissions = Set.copyOf(permissions);
    }

    void requireAll(Collection<String> required) {
        TreeSet<String> missing = new TreeSet<>(required);
        missing.removeAll(permissions);
        if (!missing.isEmpty()) {
            throw ApiException.forbidden("PERMISSION_ESCALATION",
                    "You cannot grant or revoke permissions you don't hold: " + String.join(", ", missing));
        }
    }
}
