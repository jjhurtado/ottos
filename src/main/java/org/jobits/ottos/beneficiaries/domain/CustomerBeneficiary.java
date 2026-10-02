package org.jobits.ottos.beneficiaries.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** A customer sends to this beneficiary. */
@Entity
@Table(name = "customer_beneficiaries")
public class CustomerBeneficiary {

    @EmbeddedId
    private Key key;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    protected CustomerBeneficiary() {
        // required by JPA
    }

    public CustomerBeneficiary(UUID customerId, UUID beneficiaryId, Instant linkedAt) {
        this.key = new Key(customerId, beneficiaryId);
        this.linkedAt = linkedAt;
    }

    public UUID getCustomerId() {
        return key.customerId;
    }

    public UUID getBeneficiaryId() {
        return key.beneficiaryId;
    }

    @Embeddable
    public record Key(@Column(name = "customer_id") UUID customerId,
                      @Column(name = "beneficiary_id") UUID beneficiaryId) implements Serializable {
    }
}
