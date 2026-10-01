package org.jobits.ottos.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A named set of permissions that administrators manage at runtime. Built-in roles (ADMIN) come from
 * migrations and cannot be edited or deleted through the API.
 */
@Entity
@Table(name = "roles")
public class Role {

    public static final String ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column
    private String description;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToMany
    @JoinTable(name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_code"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
        // required by JPA
    }

    public Role(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public void describe(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void replacePermissions(Collection<Permission> permissions) {
        this.permissions.clear();
        this.permissions.addAll(permissions);
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }
}
