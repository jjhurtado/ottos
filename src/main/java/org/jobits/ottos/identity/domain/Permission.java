package org.jobits.ottos.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * A capability the code checks, e.g. {@code users:read}. The catalog is maintained through Flyway migrations,
 * never through the API, because a permission only means something if some endpoint enforces it.
 */
@Entity
@Immutable
@Table(name = "permissions")
public class Permission {

    @Id
    @Column(length = 100)
    private String code;

    @Column(nullable = false, length = 50)
    private String module;

    @Column(nullable = false)
    private String description;

    protected Permission() {
        // required by JPA
    }

    public String getCode() {
        return code;
    }

    public String getModule() {
        return module;
    }

    public String getDescription() {
        return description;
    }
}
