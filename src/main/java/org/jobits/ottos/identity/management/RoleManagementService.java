package org.jobits.ottos.identity.management;

import org.jobits.ottos.identity.management.Views.PermissionView;
import org.jobits.ottos.identity.management.Views.RoleView;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Roles are data: administrators create them and choose their permissions from the catalog.
 * Changes reach users the next time they log in or refresh their token (at most one access-token lifetime).
 */
public interface RoleManagementService {

    List<PermissionView> listPermissions();

    List<RoleView> list();

    RoleView get(UUID id);

    RoleView create(String code, String name, String description, Set<String> permissionCodes, Actor actor);

    RoleView describe(UUID id, String name, String description);

    RoleView replacePermissions(UUID id, Set<String> permissionCodes, Actor actor);

    void delete(UUID id);
}
