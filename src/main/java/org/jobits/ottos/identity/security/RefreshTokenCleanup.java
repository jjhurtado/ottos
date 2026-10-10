package org.jobits.ottos.identity.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes expired refresh tokens once a day, so the table only keeps tokens that can still matter. The schedule is
 * ottos.security.refresh-token-cleanup-cron (Spring cron, default 03:30) in ottos.timezone.
 */
@Component
class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final RefreshTokenService refreshTokens;

    RefreshTokenCleanup(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Scheduled(cron = "${ottos.security.refresh-token-cleanup-cron:0 30 3 * * *}", zone = "${ottos.timezone:America/Havana}")
    void deleteExpired() {
        int deleted = refreshTokens.deleteExpired();
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
    }
}
