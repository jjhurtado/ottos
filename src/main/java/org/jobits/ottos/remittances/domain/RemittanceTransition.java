package org.jobits.ottos.remittances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;

/** An allowed move between two statuses and the permission it requires. */
@Entity
@Immutable
@Table(name = "remittance_transitions")
public class RemittanceTransition {

    @EmbeddedId
    private Key key;

    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    protected RemittanceTransition() {
        // required by JPA
    }

    public String getFromStatus() {
        return key.fromStatus;
    }

    public String getToStatus() {
        return key.toStatus;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    @Embeddable
    public record Key(@Column(name = "from_status") String fromStatus,
                      @Column(name = "to_status") String toStatus) implements Serializable {
    }
}
