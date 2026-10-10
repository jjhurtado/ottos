package org.jobits.ottos.identity.security;

import org.jobits.ottos.identity.domain.RefreshToken;

import java.util.Optional;
import java.util.UUID;

/**
 * Opaque refresh tokens: 256 random bits handed to the client, stored only as a SHA-256 hash.
 * Each one is single use; {@link AuthenticationService} rotates it on every refresh.
 */
public interface RefreshTokenService {

    IssuedRefreshToken issue(UUID userId);

    Optional<RefreshToken> find(String value);

    /** Signs the user out of every device: no refresh token issued so far will work again. */
    void revokeAll(UUID userId);

    record IssuedRefreshToken(String value, long expiresInSeconds) {
    }
}
