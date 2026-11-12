package com.example.docmind.service;

import com.example.docmind.config.AppProperties;
import com.example.docmind.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterServiceTest {

    /** Clock that tests can move forward. */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final AppProperties properties = new AppProperties();
    private final RateLimiterService limiter = new RateLimiterService(properties, clock);

    @Test
    void allowsUpToTheLimitThenRejectsWithRetryAfter() {
        properties.getRateLimit().setChat(new AppProperties.Limit(2, Duration.ofHours(1)));
        limiter.consume(1L, RateLimiterService.Action.CHAT);
        clock.now = clock.now.plus(Duration.ofMinutes(20));
        limiter.consume(1L, RateLimiterService.Action.CHAT);

        assertThatThrownBy(() -> limiter.consume(1L, RateLimiterService.Action.CHAT))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("2 questions per hour")
                // the oldest request leaves the window 40 minutes from now
                .extracting("retryAfterSeconds").isEqualTo(Duration.ofMinutes(40).toSeconds());
        assertThat(limiter.usage(1L, RateLimiterService.Action.CHAT).remaining()).isZero();
    }

    @Test
    void windowSlidesSoOldRequestsStopCounting() {
        properties.getRateLimit().setChat(new AppProperties.Limit(1, Duration.ofHours(1)));
        limiter.consume(1L, RateLimiterService.Action.CHAT);
        clock.now = clock.now.plus(Duration.ofMinutes(61));
        limiter.consume(1L, RateLimiterService.Action.CHAT);
        assertThat(limiter.usage(1L, RateLimiterService.Action.CHAT).remaining()).isZero();
    }

    @Test
    void limitsAreSeparatePerUserAndPerAction() {
        properties.getRateLimit().setChat(new AppProperties.Limit(1, Duration.ofHours(1)));
        limiter.consume(1L, RateLimiterService.Action.CHAT);
        limiter.consume(2L, RateLimiterService.Action.CHAT);
        limiter.consume(1L, RateLimiterService.Action.SEARCH);
        assertThat(limiter.usage(2L, RateLimiterService.Action.CHAT).remaining()).isZero();
        assertThat(limiter.usage(3L, RateLimiterService.Action.CHAT).remaining()).isEqualTo(1);
    }

    @Test
    void disabledLimiterNeverRejects() {
        properties.getRateLimit().setEnabled(false);
        properties.getRateLimit().setChat(new AppProperties.Limit(1, Duration.ofHours(1)));
        for (int i = 0; i < 5; i++) limiter.consume(1L, RateLimiterService.Action.CHAT);
    }

    @Test
    void purgeDropsCountersThatHaveBeenIdleForADay() {
        limiter.consume(1L, RateLimiterService.Action.UPLOAD);
        clock.now = clock.now.plus(Duration.ofDays(2));
        limiter.purgeIdle();
        assertThat(limiter.usage(1L, RateLimiterService.Action.UPLOAD).remaining())
                .isEqualTo(properties.getRateLimit().getUpload().getRequests());
    }

    @Test
    void describesDurationsReadably() {
        assertThat(RateLimiterService.describe(Duration.ofHours(1))).isEqualTo("hour");
        assertThat(RateLimiterService.describe(Duration.ofDays(1))).isEqualTo("day");
        assertThat(RateLimiterService.describe(Duration.ofMinutes(40))).isEqualTo("40 min");
        assertThat(RateLimiterService.describe(Duration.ofSeconds(30))).isEqualTo("30 s");
    }
}
