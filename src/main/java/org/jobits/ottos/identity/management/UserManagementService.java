package org.jobits.ottos.identity.management;

import org.jobits.ottos.identity.management.Views.UserView;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Staff accounts managed by administrators. Guarantees that at least one active ADMIN always remains.
 */
public interface UserManagementService {

    List<UserView> list();

    UserView get(UUID id);

    UserView create(String email, String name, String password, Set<UUID> roleIds, Actor actor);

    UserView rename(UUID id, String name);

    UserView replaceRoles(UUID id, Set<UUID> roleIds, Actor actor);

    UserView deactivate(UUID id, Actor actor);

    UserView activate(UUID id);

    /** Sets a new password chosen by an administrator and signs the user out of every device. */
    void resetPassword(UUID id, String password);
}
