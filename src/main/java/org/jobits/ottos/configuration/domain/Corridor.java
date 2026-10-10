package org.jobits.ottos.configuration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** A currency pair money is sent through, e.g. USD-CUP; the amount to deliver is rounded down to deliveryRounding. */
@Entity
@Table(name = "corridors")
public class Corridor {

    @Id
    @Column(length = 7)
    private String code;

    @Column(name = "source_currency", nullable = false, length = 3)
    private String sourceCurrency;

    @Column(name = "target_currency", nullable = false, length = 3)
    private String targetCurrency;

    @Column(name = "delivery_rounding", nullable = false, precision = 19, scale = 2)
    private BigDecimal deliveryRounding;

    @Column(nullable = false)
    private boolean active;

    protected Corridor() {
        // required by JPA
    }

    public boolean isSameCurrency() {
        return sourceCurrency.equals(targetCurrency);
    }

    public String getCode() {
        return code;
    }

    public String getSourceCurrency() {
        return sourceCurrency;
    }

    public String getTargetCurrency() {
        return targetCurrency;
    }

    public BigDecimal getDeliveryRounding() {
        return deliveryRounding;
    }

    public boolean isActive() {
        return active;
    }
}
