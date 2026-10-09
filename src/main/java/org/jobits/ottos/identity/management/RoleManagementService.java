package org.jobits.ottos.identity.management;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.identity.domain.Permission;
import org.jobits.ottos.identity.domain.PermissionRepository;
import org.jobits.ottos.identity.domain.Role;
import org.jobits.ottos.identity.domain.RoleRepository;
import org.jobits.ottos.identity.domain.UserRepository;
import org.jobits.ottos.identity.management.Views.PermissionView;
import org.jobits.ottos.identity.management.Views.RoleView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Roles are data: administrators create them and choose their permissions from the catalog.
 * Changes reach users the next time they log in or refresh their token (at most one access-token lifetime).
 */
@Service
public class RoleManagementService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserRepository users;

    RoleManagementService(RoleRepository roles, PermissionRepository permissions, UserRepository users) {
        this.roles = roles;
        this.permissions = permissions;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<PermissionView> listPermissions() {
        return permissions.findAllByOrderByModuleAscCodeAsc().stream().map(PermissionView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleView> list() {
        return roles.findAllByOrderByCodeAsc().stream().map(RoleView::of).toList();
    }

    @Transactional(readOnly = true)
    public RoleView get(UUID id) {
        return RoleView.of(find(id));
    }

    @Transactional
    public RoleView create(String code, String name, String description, Set<String> permissionCodes, Actor actor) {
        if (roles.existsByCode(code)) {
            throw ApiException.conflict("ROLE_CODE_TAKEN", "A role with code " + code + " already exists");
        }
        List<Permission> granted = resolve(permissionCodes);
        actor.requireAll(permissionCodes);
        Role role = new Role(code, name, description);
        role.replacePermissions(granted);
        return RoleView.of(roles.save(role));
    }

    @Transactional
    public RoleView describe(UUID id, String name, String description) {
        Role role = findEditable(id);
        role.describe(name, description);
        return RoleView.of(role);
    }

    @Transactional
    public RoleView replacePermissions(UUID id, Set<String> permissionCodes, Actor actor) {
        Role role = findEditable(id);
        List<Permission> granted = resolve(permissionCodes);
        Set<String> changed = new HashSet<>(permissionCodes);
        role.getPermissions().forEach(p -> {
            if (!changed.remove(p.getCode())) {
                changed.add(p.getCode());
            }
        });
        actor.requireAll(changed);
        role.replacePermissions(granted);
        return RoleView.of(role);
    }

    @Transactional
    public void delete(UUID id) {
        Role role = findEditable(id);
        long holders = users.countByRoles_Id(id);
        if (holders > 0) {
            throw ApiException.conflict("ROLE_IN_USE",
                    "Role " + role.getCode() + " is assigned to " + holders + " user(s); remove it from them first");
        }
        roles.delete(role);
    }

    private Role find(UUID id) {
        return roles.findWithPermissionsById(id)
                .orElseThrow(() -> ApiException.notFound("ROLE_NOT_FOUND", "Role not found"));
    }

    private Role findEditable(UUID id) {
        Role role = find(id);
        if (role.isBuiltIn()) {
            throw ApiException.conflict("BUILT_IN_ROLE", "Built-in role " + role.getCode() + " cannot be modified");
        }
        return role;
    }

    private List<Permission> resolve(Set<String> codes) {
        List<Permission> found = permissions.findAllById(codes);
        if (found.size() != codes.size()) {
            TreeSet<String> unknown = new TreeSet<>(codes);
            found.forEach(p -> unknown.remove(p.getCode()));
            throw ApiException.badRequest("UNKNOWN_PERMISSIONS", "Unknown permissions: " + String.join(", ", unknown));
        }
        return found;
    }
}
