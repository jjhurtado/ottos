package org.jobits.ottos.identity;

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
                .map(u -> new StaffMember(u.getId(), u.getName(), u.getEmail(), u.isActive(), u.permissionCodes()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffMember> activeWith(String permission) {
        return users.findActiveWithPermission(permission).stream()
                .map(u -> new StaffMember(u.getId(), u.getName(), u.getEmail(), u.isActive(), Set.of(permission)))
                .toList();
    }
}
