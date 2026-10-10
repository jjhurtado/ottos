package org.jobits.ottos.configuration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jobits.ottos.configuration.ServiceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Units of the target currency per unit of the source currency for one kind of remittance, from validFrom on.
 * Never edited.
 */
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "corridor_code", nullable = false, length = 7)
    private String corridorCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "remittance_type", nullable = false, length = 20)
    private ServiceType remittanceType;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal rate;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "created_by")
    private UUID createdBy;

    protected ExchangeRate() {
        // required by JPA
    }

    public ExchangeRate(String corridorCode, ServiceType remittanceType, BigDecimal rate, Instant validFrom,
                        UUID createdBy) {
        this.corridorCode = corridorCode;
        this.remittanceType = remittanceType;
        this.rate = rate;
        this.validFrom = validFrom;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public String getCorridorCode() {
        return corridorCode;
    }

    public ServiceType getRemittanceType() {
        return remittanceType;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
