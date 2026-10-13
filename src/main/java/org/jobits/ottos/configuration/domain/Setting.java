package org.jobits.ottos.configuration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One configurable value of the business, stored as text and read through a typed accessor. */
@Entity
@Table(name = "settings", schema = "configuration")
public class Setting {

    /** The currency the business charges in and keeps its accounts in; set by the installation's setup script. */
    public static final String BUSINESS_CURRENCY = "business_currency";
    /** Trailing digits that identify a phone when matching old records; set by the installation's setup script. */
    public static final String PHONE_MATCH_DIGITS = "phone_match_digits";
    /** Smallest amount a remittance can send, in the business currency. 0 means no minimum. */
    public static final String MINIMUM_AMOUNT = "minimum_amount";
    /** Days from registration to the expected delivery date. */
    public static final String DEFAULT_DELIVERY_DAYS = "default_delivery_days";
    /** How far, in percent, a courier's exchange rate may stray from the configured one before it is flagged. */
    public static final String EXCHANGE_RATE_TOLERANCE = "exchange_rate_tolerance_percent";

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
