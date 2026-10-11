package org.jobits.ottos.identity;

import org.jobits.ottos.identity.domain.Role;
import org.jobits.ottos.identity.domain.User;
import org.jobits.ottos.identity.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Implementation of {@link StaffDirectoryService}. */
@Service
class StaffDirectoryServiceImpl implements StaffDirectoryService {

    private final UserRepository users;

    StaffDirectoryServiceImpl(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffMember> find(UUID id) {
        return users.findWithRolesById(id)
                .map(u -> new StaffMember(u.getId(), u.getName(), u.getEmail(), u.isActive(), administrator(u),
                        u.permissionCodes()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffMember> couriers() {
        return users.findActiveWithPermission(COURIER_PERMISSION).stream()
                .filter(u -> !administrator(u))
                .map(u -> new StaffMember(u.getId(), u.getName(), u.getEmail(), u.isActive(), false,
                        Set.of(COURIER_PERMISSION)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCourier(UUID id) {
        return find(id).map(StaffMember::isCourier).orElse(false);
    }

    private static boolean administrator(User user) {
        return user.getRoles().stream().anyMatch(Role::isBuiltIn);
    }
}
