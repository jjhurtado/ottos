package org.jobits.ottos.identity.domain;

import org.jobits.ottos.identity.ClientType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Long-lived token used to obtain new access tokens. Only its SHA-256 hash is stored. */
@Entity
@Table(name = "refresh_tokens", schema = "identity")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ClientType client;

    /** The token issued when this one was used; null if it was revoked another way (logout, sign out everywhere). */
    @Column(name = "replaced_by")
    private UUID replacedBy;

    protected RefreshToken() {
        // required by JPA
    }

    public RefreshToken(UUID userId, ClientType client, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.client = client;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    /** Used to issue a new pair: revoked now (if not yet) and replaced by the token with that id. */
    public void replaceWith(UUID successorId, Instant now) {
        revoke(now);
        this.replacedBy = successorId;
    }

    /** True if it was used to issue a new pair no longer ago than grace: a retry, not a stolen token. */
    public boolean isRecentlyReplaced(Instant now, Duration grace) {
        return replacedBy != null && revokedAt != null && !now.isAfter(revokedAt.plus(grace));
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public ClientType getClient() {
        return client;
    }

    public UUID getReplacedBy() {
        return replacedBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
