package org.jobits.ottos.branches.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "municipalities")
public class Municipality {

    @Id
    @Column(length = 4)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "province_code")
    private Province province;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected Municipality() {
        // required by JPA
    }

    public String getCode() {
        return code;
    }

    public Province getProvince() {
        return province;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
