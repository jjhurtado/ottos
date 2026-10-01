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
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/** A staff account (admin, sales, delivery…). Customers live in their own module. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToMany
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {
        // required by JPA
    }

    public User(String email, String passwordHash, String name) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void replaceRoles(Collection<Role> roles) {
        this.roles.clear();
        this.roles.addAll(roles);
    }

    public boolean hasRole(String roleCode) {
        return roles.stream().anyMatch(r -> r.getCode().equals(roleCode));
    }

    public SortedSet<String> roleCodes() {
        TreeSet<String> codes = new TreeSet<>();
        roles.forEach(r -> codes.add(r.getCode()));
        return codes;
    }

    /** Union of the permissions of every role the user holds. */
    public SortedSet<String> permissionCodes() {
        TreeSet<String> codes = new TreeSet<>();
        roles.forEach(r -> r.getPermissions().forEach(p -> codes.add(p.getCode())));
        return codes;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
