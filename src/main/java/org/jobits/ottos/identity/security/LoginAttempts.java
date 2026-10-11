package org.jobits.ottos.identity.security;

import org.jobits.ottos.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts failed logins per email and per IP and locks them for a while when they exceed {@link LoginLimits}. Kept in
 * memory: one instance of the backend runs, and a restart only forgets the counters. An unknown email is counted and
 * locked like any other, so a lock never reveals which accounts exist.
 */
@Component
public class LoginAttempts {

    private static final Logger log = LoggerFactory.getLogger(LoginAttempts.class);

    private final Map<String, Tracker> trackers = new ConcurrentHashMap<>();
    private final LoginLimits limits;
    private final Clock clock;

    LoginAttempts(LoginLimits limits, Clock clock) {
        this.limits = limits;
        this.clock = clock;
    }

    /** Fails with 429 TOO_MANY_LOGIN_ATTEMPTS if the email or the IP is locked. */
    public void check(String email, String ip) {
        Instant now = clock.instant();
        Duration wait = max(remaining(account(email), now), remaining(address(ip), now));
        if (!wait.isZero()) {
            // Rounded up, so the client never retries a moment too early
            long seconds = Math.max(1, (wait.toMillis() + 999) / 1000);
            throw ApiException.tooManyRequests("TOO_MANY_LOGIN_ATTEMPTS",
                            "Too many failed logins; try again in " + Math.max(1, (seconds + 59) / 60) + " minutes")
                    .with("retryAfterSeconds", seconds)
                    .withHeader("Retry-After", Long.toString(seconds));
        }
    }

    public void failed(String email, String ip) {
        Instant now = clock.instant();
        record(account(email), limits.maxFailuresPerAccount(), now);
        record(address(ip), limits.maxFailuresPerIp(), now);
    }

    /** A successful login clears the email's failures (not the IP's). */
    public void succeeded(String email) {
        trackers.remove(account(email));
    }

    /** Forgets every counter and lock. */
    public void clear() {
        trackers.clear();
    }

    @Scheduled(fixedDelay = 600_000)
    void forgetOld() {
        Instant now = clock.instant();
        trackers.entrySet().removeIf(e -> e.getValue().isStale(now, limits.window()));
    }

    private void record(String key, int max, Instant now) {
        trackers.compute(key, (k, tracker) -> {
            Tracker t = tracker == null ? new Tracker() : tracker;
            if (t.fail(now, limits.window(), max, limits.lockout())) {
                log.warn("Login locked for {} until {}", k, t.lockedUntil);
            }
            return t;
        });
    }

    private Duration remaining(String key, Instant now) {
        Tracker t = trackers.get(key);
        return t == null || t.lockedUntil == null || !t.lockedUntil.isAfter(now)
                ? Duration.ZERO : Duration.between(now, t.lockedUntil);
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static String account(String email) {
        return "account:" + (email == null ? "" : email.trim().toLowerCase());
    }

    private static String address(String ip) {
        return "ip:" + (ip == null ? "" : ip);
    }

    /** Failures of one key within the window, and the end of its lock if locked. */
    private static final class Tracker {

        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant lockedUntil;

        /** Returns true if this failure locks the key. */
        synchronized boolean fail(Instant now, Duration window, int max, Duration lockout) {
            while (!failures.isEmpty() && !failures.peekFirst().isAfter(now.minus(window))) {
                failures.pollFirst();
            }
            failures.addLast(now);
            if (failures.size() >= max) {
                lockedUntil = now.plus(lockout);
                failures.clear();
                return true;
            }
            return false;
        }

        synchronized boolean isStale(Instant now, Duration window) {
            boolean unlocked = lockedUntil == null || !lockedUntil.isAfter(now);
            boolean noRecent = failures.isEmpty() || !failures.peekLast().isAfter(now.minus(window));
            return unlocked && noRecent;
        }
    }
}
