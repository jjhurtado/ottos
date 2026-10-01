package org.jobits.ottos.identity.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithRolesById(UUID id);

    @EntityGraph(attributePaths = "roles")
    List<User> findAllByOrderByNameAsc();

    boolean existsByEmail(String email);

    boolean existsByRoles_Code(String roleCode);

    long countByRoles_Id(UUID roleId);

    @Query("""
            select count(distinct u) from User u join u.roles r
            where r.code = :roleCode and u.active = true and u.id <> :excludedUserId""")
    long countActiveWithRoleExcluding(String roleCode, UUID excludedUserId);
}
