package org.jobits.ottos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Business time. "Today" (for due dates, late remittances and monthly reports) is the date in ottos.timezone, the
 * installation's time zone (OTTOS_TIMEZONE, e.g. America/Havana). Required: there is no default, so an installation
 * cannot silently run on the wrong day.
 * Inject the {@link Clock} instead of calling now() so tests can control time.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock(@Value("${ottos.timezone:}") String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalStateException("Missing ottos.timezone (env OTTOS_TIMEZONE), e.g. America/Havana");
        }
        return Clock.system(ZoneId.of(timezone.trim()));
    }
}
