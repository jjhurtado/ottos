package org.jobits.ottos.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Limits on failed logins (ottos.security.login.*), against password guessing.
 *
 * @param maxFailuresPerAccount failed logins on one email within the window that lock it
 * @param maxFailuresPerIp      failed logins from one IP within the window, on any email, that lock that IP; high on
 *                              purpose, since several people can share an IP behind a proxy
 * @param window                how far back failures count
 * @param lockout               how long a lock lasts
 */
@ConfigurationProperties(prefix = "ottos.security.login")
public record LoginLimits(
        @DefaultValue("5") int maxFailuresPerAccount,
        @DefaultValue("50") int maxFailuresPerIp,
        @DefaultValue("15m") Duration window,
        @DefaultValue("15m") Duration lockout
) {
}
