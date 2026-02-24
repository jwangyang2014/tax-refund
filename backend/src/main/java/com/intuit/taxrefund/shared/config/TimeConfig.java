package com.intuit.taxrefund.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provide time related beans, so that we can mock time in tests if needed.
 * We inject Clock instead of using static methods like Instant.now() directly, to make it easier to control time in tests.
 * Refer to PolicySnippets for an example of how to use the injected Clock.
 * On production, we can switch to a more sophisticated clock implementation if needed (e.g. one that syncs with an NTP server).
 * For now, we just use the system UTC clock.
 * Example code for testing
 * Clock fixed = Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC);
 * PolicySnippets service = new PolicySnippets(repo, fixed);
 * Now Instant.now(clock) is always 2026-02-01T00:00:00Z and the test is deterministic.
 * Another Example: Reproducing a production issue
 * Suppose legal updates a policy and someone reports:
 * “On Jan 1 at 00:05 UTC, users were still seeing the old snippet.”
 * With Clock, you can reproduce exactly by setting:
 * Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:05:00Z"), ZoneOffset.UTC);
 */
@Configuration
public class TimeConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}