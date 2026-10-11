package org.jobits.ottos.identity;

/** Where a session was opened; it decides how long its access tokens last. */
public enum ClientType {
    /** The web panel: short access tokens (ottos.security.access-token-ttl). */
    WEB,
    /** The mobile app, which works offline for a whole day: ottos.security.mobile-access-token-ttl. */
    MOBILE
}
