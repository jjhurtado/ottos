package org.jobits.ottos.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One currency of the business box, of a courier, or of the outside world. */
@Entity
@Table(name = "cash_accounts")
public class CashAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_key", nullable = false, unique = true, length = 80)
    private String key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CashAccount() {
        // required by JPA
    }

    public CashAccount(AccountType type, UUID ownerId, String currency, Instant createdAt) {
        this.key = keyOf(type, ownerId, currency);
        this.type = type;
        this.ownerId = ownerId;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public static String keyOf(AccountType type, UUID ownerId, String currency) {
        return ownerId == null ? type + ":" + currency : type + ":" + ownerId + ":" + currency;
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public AccountType getType() {
        return type;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getCurrency() {
        return currency;
    }
}
