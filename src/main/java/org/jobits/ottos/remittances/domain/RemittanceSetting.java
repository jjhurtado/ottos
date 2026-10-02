package org.jobits.ottos.remittances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Key-value settings of the module, e.g. default_delivery_days. */
@Entity
@Table(name = "remittance_settings")
public class RemittanceSetting {

    public static final String DEFAULT_DELIVERY_DAYS = "default_delivery_days";

    @Id
    @Column(name = "setting_key", length = 50)
    private String key;

    @Column(name = "setting_value", nullable = false)
    private String value;

    protected RemittanceSetting() {
        // required by JPA
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
