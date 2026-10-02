package org.jobits.ottos.remittances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** A step of the workflow. Defined in the database; the code relies only on its flags. */
@Entity
@Immutable
@Table(name = "remittance_statuses")
public class RemittanceStatus {

    @Id
    @Column(length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_initial", nullable = false)
    private boolean initial;

    @Column(name = "requires_courier", nullable = false)
    private boolean requiresCourier;

    @Column(name = "is_final", nullable = false)
    private boolean finalStatus;

    protected RemittanceStatus() {
        // required by JPA
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    public boolean isInitial() {
        return initial;
    }

    public boolean isRequiresCourier() {
        return requiresCourier;
    }

    public boolean isFinalStatus() {
        return finalStatus;
    }
}
