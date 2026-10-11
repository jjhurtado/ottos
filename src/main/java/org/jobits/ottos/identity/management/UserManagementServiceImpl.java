package org.jobits.ottos.identity.management;

import org.jobits.ottos.ApiException;
import org.jobits.ottos.identity.domain.Permission;
import org.jobits.ottos.identity.domain.Role;
import org.jobits.ottos.identity.domain.RoleRepository;
import org.jobits.ottos.identity.domain.User;
import org.jobits.ottos.identity.domain.UserRepository;
import org.jobits.ottos.identity.management.Views.UserView;
import org.jobits.ottos.identity.security.RefreshTokenService;
import org.jobits.ottos.identity.security.StaffAudit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Implementation of {@link UserManagementService}. */
@Service
class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final StaffAudit audit;

    UserManagementServiceImpl(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                          RefreshTokenService refreshTokens, StaffAudit audit) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserView> list() {
        return users.findAllByOrderByNameAsc().stream().map(UserView::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserView get(UUID id) {
        return UserView.of(find(id));
    }

    @Override
    @Transactional
    public UserView create(String email, String name, String password, Set<UUID> roleIds, Actor actor) {
        if (users.existsByEmail(User.normalizeEmail(email))) {
            throw ApiException.conflict("EMAIL_TAKEN", "A user with that email already exists");
        }
        List<Role> assigned = resolve(roleIds);
        actor.requireAll(permissionsOf(assigned));
        User user = new User(email, passwordEncoder.encode(password), name);
        user.replaceRoles(assigned);
        User saved = users.save(user);
        audit.userCreated(saved, actor.userId());
        return UserView.of(saved);
    }

    @Override
    @Transactional
    public UserView rename(UUID id, String name, Actor actor) {
        User user = find(id);
        String previous = user.getName();
        user.rename(name);
        audit.userRenamed(user, previous, actor.userId());
        return UserView.of(user);
    }

    @Override
    @Transactional
    public UserView replaceRoles(UUID id, Set<UUID> roleIds, Actor actor) {
        User user = find(id);
        List<Role> assigned = resolve(roleIds);

        Set<Role> changed = new HashSet<>(user.getRoles());
        assigned.forEach(r -> {
            if (!changed.remove(r)) {
                changed.add(r);
            }
        });
        actor.requireAll(permissionsOf(changed));

        boolean losesAdmin = user.hasRole(Role.ADMIN) && assigned.stream().noneMatch(r -> r.getCode().equals(Role.ADMIN));
        if (losesAdmin && user.isActive()) {
            requireAnotherActiveAdmin(user);
        }
        List<String> before = List.copyOf(user.roleCodes());
        user.replaceRoles(assigned);
        audit.userRolesChanged(user, before, actor.userId());
        return UserView.of(user);
    }

    @Override
    @Transactional
    public UserView deactivate(UUID id, Actor actor) {
        User user = find(id);
        if (user.getId().equals(actor.userId())) {
            throw ApiException.conflict("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own account");
        }
        if (user.isActive() && user.hasRole(Role.ADMIN)) {
            requireAnotherActiveAdmin(user);
        }
        user.deactivate();
        refreshTokens.revokeAll(user.getId());
        audit.userDeactivated(user, actor.userId());
        return UserView.of(user);
    }

    @Override
    @Transactional
    public UserView activate(UUID id, Actor actor) {
        User user = find(id);
        user.activate();
        audit.userActivated(user, actor.userId());
        return UserView.of(user);
    }

    @Override
    @Transactional
    public void resetPassword(UUID id, String password, Actor actor) {
        User user = find(id);
        user.changePasswordHash(passwordEncoder.encode(password));
        refreshTokens.revokeAll(user.getId());
        audit.userPasswordReset(user, actor.userId());
    }

    private User find(UUID id) {
        return users.findWithRolesById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
    }

    private void requireAnotherActiveAdmin(User user) {
        if (users.countActiveWithRoleExcluding(Role.ADMIN, user.getId()) == 0) {
            throw ApiException.conflict("LAST_ADMIN", "At least one active administrator must remain");
        }
    }

    private List<Role> resolve(Set<UUID> ids) {
        List<Role> found = roles.findAllById(ids);
        if (found.size() != ids.size()) {
            TreeSet<String> unknown = new TreeSet<>();
            ids.forEach(id -> unknown.add(id.toString()));
            found.forEach(r -> unknown.remove(r.getId().toString()));
            throw ApiException.badRequest("UNKNOWN_ROLES", "Unknown roles: " + String.join(", ", unknown));
        }
        return found;
    }

    private static Set<String> permissionsOf(Iterable<Role> roles) {
        Set<String> codes = new HashSet<>();
        roles.forEach(r -> r.getPermissions().stream().map(Permission::getCode).forEach(codes::add));
        return codes;
    }
}
