package org.jobits.ottos.customers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A remittance sender. The phone (normalized) identifies them. */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, unique = true, length = 30)
    private String phone;

    @Column
    private String email;

    @Column(name = "document_type", length = 20)
    private String documentType;

    @Column(name = "document_number", length = 50)
    private String documentNumber;

    @Column
    private String address;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    protected Customer() {
        // required by JPA
    }

    public Customer(Details details, Instant createdAt, UUID createdBy) {
        update(details);
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public void update(Details details) {
        this.fullName = details.fullName();
        this.phone = details.phone();
        this.email = details.email();
        this.documentType = details.documentType();
        this.documentNumber = details.documentNumber();
        this.address = details.address();
        this.notes = details.notes();
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getAddress() {
        return address;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    /** Editable data; the phone must already be normalized. */
    public record Details(String fullName, String phone, String email, String documentType, String documentNumber,
                          String address, String notes) {
    }
}
