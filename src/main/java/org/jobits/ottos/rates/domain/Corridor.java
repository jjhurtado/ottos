package org.jobits.ottos.rates.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** A currency pair money is sent through, e.g. USD-CUP. */
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

    /** Rounds an amount in the target currency down to a multiple of the delivery rounding (e.g. 50 CUP). */
    public BigDecimal roundForDelivery(BigDecimal amount) {
        return amount.divide(deliveryRounding, 0, RoundingMode.FLOOR)
                .multiply(deliveryRounding)
                .setScale(2, RoundingMode.UNNECESSARY);
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
