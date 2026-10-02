package org.jobits.ottos.beneficiaries.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beneficiaries")
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(name = "alternate_phone", length = 30)
    private String alternatePhone;

    @Column(nullable = false, length = 150)
    private String street;

    @Column(name = "house_number", length = 30)
    private String houseNumber;

    @Column(name = "between_streets", length = 150)
    private String betweenStreets;

    @Column(length = 100)
    private String neighborhood;

    @Column(name = "municipality_code", nullable = false, length = 4)
    private String municipalityCode;

    @Column
    private String reference;

    @Column(name = "document_number", length = 50)
    private String documentNumber;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    protected Beneficiary() {
        // required by JPA
    }

    public Beneficiary(Details details, Instant createdAt, UUID createdBy) {
        update(details);
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public void update(Details d) {
        this.fullName = d.fullName();
        this.phone = d.phone();
        this.alternatePhone = d.alternatePhone();
        this.street = d.street();
        this.houseNumber = d.houseNumber();
        this.betweenStreets = d.betweenStreets();
        this.neighborhood = d.neighborhood();
        this.municipalityCode = d.municipalityCode();
        this.reference = d.reference();
        this.documentNumber = d.documentNumber();
        this.notes = d.notes();
    }

    public Details details() {
        return new Details(fullName, phone, alternatePhone, street, houseNumber, betweenStreets, neighborhood,
                municipalityCode, reference, documentNumber, notes);
    }

    public UUID getId() {
        return id;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Editable data; phones must already be normalized. */
    public record Details(String fullName, String phone, String alternatePhone, String street, String houseNumber,
                          String betweenStreets, String neighborhood, String municipalityCode, String reference,
                          String documentNumber, String notes) {
    }
}
