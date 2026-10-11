package org.jobits.ottos.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Security settings.
 *
 * @param jwtSecret       Base64-encoded HMAC key (at least 32 decoded bytes). Provide it via environment variable in production.
 * @param accessTokenTtl  lifetime of an access token issued to the web panel. Changes to a user's roles or permissions
 *                        reach them within this time.
 * @param mobileAccessTokenTtl lifetime of an access token issued to the mobile app: a whole working day, so it works
 *                        offline until it syncs.
 * @param refreshReuseGrace how long after a refresh token is used a retry with it still gets a new pair (the response
 *                        may have been lost) instead of signing the user out everywhere.
 * @param refreshTokenTtl lifetime of a refresh token.
 * @param issuer          issuer written into, and required from, every JWT.
 */
@ConfigurationProperties(prefix = "ottos.security")
public record JwtProperties(
        String jwtSecret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("14h") Duration mobileAccessTokenTtl,
        @DefaultValue("60s") Duration refreshReuseGrace,
        @DefaultValue("30d") Duration refreshTokenTtl,
        @DefaultValue("ottos") String issuer
) {
}
