package org.jobits.ottos.configuration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One configurable value of the business, stored as text and read through a typed accessor. */
@Entity
@Table(name = "settings")
public class Setting {

    /** Smallest amount a remittance can send, in USD. 0 means no minimum. */
    public static final String MINIMUM_AMOUNT = "minimum_amount";
    /** Days from registration to the expected delivery date. */
    public static final String DEFAULT_DELIVERY_DAYS = "default_delivery_days";

    @Id
    @Column(name = "setting_key", length = 50)
    private String key;

    @Column(name = "setting_value", nullable = false)
    private String value;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected Setting() {
        // required by JPA
    }

    public void change(String value, Instant at, UUID by) {
        this.value = value;
        this.updatedAt = at;
        this.updatedBy = by;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
