package org.jobits.ottos.identity.security;

import org.jobits.ottos.identity.domain.RefreshToken;
import org.jobits.ottos.identity.domain.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Opaque refresh tokens: 256 random bits handed to the client, stored only as a SHA-256 hash.
 * Each one is single use; {@link AuthenticationService} rotates it on every refresh.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final JwtProperties props;

    RefreshTokenService(RefreshTokenRepository tokens, JwtProperties props) {
        this.tokens = tokens;
        this.props = props;
    }

    @Transactional
    public IssuedRefreshToken issue(UUID userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        tokens.save(new RefreshToken(userId, hash(value), now, now.plus(props.refreshTokenTtl())));
        return new IssuedRefreshToken(value, props.refreshTokenTtl().toSeconds());
    }

    Optional<RefreshToken> find(String value) {
        return tokens.findByTokenHash(hash(value));
    }

    /** Signs the user out of every device: no refresh token issued so far will work again. */
    @Transactional
    public void revokeAll(UUID userId) {
        tokens.revokeAllForUser(userId, Instant.now());
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record IssuedRefreshToken(String value, long expiresInSeconds) {
    }
}
