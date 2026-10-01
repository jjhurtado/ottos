package org.jobits.ottos.identity.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = "permissions")
    List<Role> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findWithPermissionsById(UUID id);
}
