package org.jobits.ottos.configuration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jobits.ottos.configuration.FeeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** How the fee of a corridor is calculated, from validFrom on. Never edited: a change inserts a new rule. */
@Entity
@Table(name = "fee_rules")
public class FeeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "corridor_code", nullable = false, length = 7)
    private String corridorCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeeType type;

    @Column(name = "fee_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal value;

    @Column(name = "min_fee", precision = 19, scale = 2)
    private BigDecimal minFee;

    @Column(name = "max_fee", precision = 19, scale = 2)
    private BigDecimal maxFee;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "created_by")
    private UUID createdBy;

    protected FeeRule() {
        // required by JPA
    }

    public FeeRule(String corridorCode, FeeType type, BigDecimal value, BigDecimal minFee, BigDecimal maxFee,
                   Instant validFrom, UUID createdBy) {
        this.corridorCode = corridorCode;
        this.type = type;
        this.value = value;
        this.minFee = minFee;
        this.maxFee = maxFee;
        this.validFrom = validFrom;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public String getCorridorCode() {
        return corridorCode;
    }

    public FeeType getType() {
        return type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public BigDecimal getMinFee() {
        return minFee;
    }

    public BigDecimal getMaxFee() {
        return maxFee;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
