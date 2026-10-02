package org.jobits.ottos.remittances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.jobits.ottos.remittances.RemittanceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A delivery or pickup. Keeps a copy of the quote and of the beneficiary as they were when it was registered,
 * so later changes to rates, fees or the beneficiary never alter it.
 */
@Entity
@Table(name = "remittances")
public class Remittance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 12)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RemittanceType type;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "beneficiary_id", nullable = false)
    private UUID beneficiaryId;

    @Column(name = "beneficiary_name", nullable = false, length = 150)
    private String beneficiaryName;

    @Column(name = "beneficiary_phone", nullable = false, length = 30)
    private String beneficiaryPhone;

    @Column(name = "beneficiary_alternate_phone", length = 30)
    private String beneficiaryAlternatePhone;

    @Column(name = "beneficiary_address", nullable = false, length = 500)
    private String beneficiaryAddress;

    @Column(name = "beneficiary_municipality_code", nullable = false, length = 4)
    private String beneficiaryMunicipalityCode;

    @Column(name = "beneficiary_reference")
    private String beneficiaryReference;

    @Column(name = "corridor_code", nullable = false, length = 7)
    private String corridorCode;

    @Column(name = "source_currency", nullable = false, length = 3)
    private String sourceCurrency;

    @Column(name = "target_currency", nullable = false, length = 3)
    private String targetCurrency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fee;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal rate;

    @Column(name = "amount_to_deliver", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountToDeliver;

    @Column(name = "exchange_rate_id", nullable = false)
    private UUID exchangeRateId;

    @Column(name = "fee_rule_id", nullable = false)
    private UUID feeRuleId;

    @Column(nullable = false, length = 6)
    private String pin;

    @Column(name = "courier_id")
    private UUID courierId;

    @Column(name = "expected_date", nullable = false)
    private LocalDate expectedDate;

    @Column(nullable = false)
    private int postponements;

    @Column(length = 1000)
    private String notes;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Version
    private long version;

    protected Remittance() {
        // required by JPA
    }

    public Remittance(String code, RemittanceType type, String initialStatus, UUID customerId,
                      BeneficiarySnapshot beneficiary, QuoteSnapshot quote, String pin, LocalDate expectedDate,
                      String notes, String idempotencyKey, Instant createdAt, UUID createdBy) {
        this.code = code;
        this.type = type;
        this.status = initialStatus;
        this.customerId = customerId;
        this.beneficiaryId = beneficiary.id();
        this.beneficiaryName = beneficiary.name();
        this.beneficiaryPhone = beneficiary.phone();
        this.beneficiaryAlternatePhone = beneficiary.alternatePhone();
        this.beneficiaryAddress = beneficiary.address();
        this.beneficiaryMunicipalityCode = beneficiary.municipalityCode();
        this.beneficiaryReference = beneficiary.reference();
        this.corridorCode = quote.corridor();
        this.sourceCurrency = quote.sourceCurrency();
        this.targetCurrency = quote.targetCurrency();
        this.amount = quote.amount();
        this.fee = quote.fee();
        this.total = quote.total();
        this.rate = quote.rate();
        this.amountToDeliver = quote.amountToDeliver();
        this.exchangeRateId = quote.exchangeRateId();
        this.feeRuleId = quote.feeRuleId();
        this.pin = pin;
        this.expectedDate = expectedDate;
        this.notes = notes;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public void moveTo(String status) {
        this.status = status;
    }

    public void assignTo(UUID courierId) {
        this.courierId = courierId;
    }

    public void complete(Instant at, UUID by) {
        this.completedAt = at;
        this.completedBy = by;
    }

    public void postpone(LocalDate newDate) {
        this.expectedDate = newDate;
        this.postponements++;
    }

    /** Cash that changes hands when the courier completes it: handed over for deliveries, collected for pickups. */
    public BigDecimal cashAmount() {
        return type == RemittanceType.DELIVERY ? amountToDeliver : amount;
    }

    public String cashCurrency() {
        return type == RemittanceType.DELIVERY ? targetCurrency : sourceCurrency;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public RemittanceType getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getBeneficiaryId() {
        return beneficiaryId;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public String getBeneficiaryPhone() {
        return beneficiaryPhone;
    }

    public String getBeneficiaryAlternatePhone() {
        return beneficiaryAlternatePhone;
    }

    public String getBeneficiaryAddress() {
        return beneficiaryAddress;
    }

    public String getBeneficiaryMunicipalityCode() {
        return beneficiaryMunicipalityCode;
    }

    public String getBeneficiaryReference() {
        return beneficiaryReference;
    }

    public String getCorridorCode() {
        return corridorCode;
    }

    public String getSourceCurrency() {
        return sourceCurrency;
    }

    public String getTargetCurrency() {
        return targetCurrency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getAmountToDeliver() {
        return amountToDeliver;
    }

    public String getPin() {
        return pin;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public int getPostponements() {
        return postponements;
    }

    public String getNotes() {
        return notes;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getCompletedBy() {
        return completedBy;
    }

    public record BeneficiarySnapshot(UUID id, String name, String phone, String alternatePhone, String address,
                                      String municipalityCode, String reference) {
    }

    public record QuoteSnapshot(String corridor, String sourceCurrency, String targetCurrency, BigDecimal amount,
                                BigDecimal fee, BigDecimal total, BigDecimal rate, BigDecimal amountToDeliver,
                                UUID exchangeRateId, UUID feeRuleId) {
    }
}
