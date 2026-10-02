package org.jobits.ottos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Business time. "Today" (for due dates and late remittances) is the date in ottos.timezone, Cuba by default.
 * Inject the {@link Clock} instead of calling now() so tests can control time.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock(@Value("${ottos.timezone:America/Havana}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
