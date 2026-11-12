package com.example.docmind.service;

import com.example.docmind.config.AppProperties;
import com.example.docmind.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user sliding-window limits for the endpoints that cost AI tokens (chat, search, upload).
 * State is kept in memory, which suits a single instance; counters reset when the process restarts.
 */
@Service
@RequiredArgsConstructor
public class RateLimiterService {
    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    public enum Action {
        CHAT("questions"),
        SEARCH("searches"),
        UPLOAD("uploads");

        private final String noun;

        Action(String noun) {
            this.noun = noun;
        }
    }

    public record Usage(int limit, int remaining, long windowSeconds) {
    }

    private final AppProperties appProperties;
    private final Clock clock;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /** Records one request, or throws 429 when the user's budget for this window is used up. */
    public void consume(Long userId, Action action) {
        AppProperties.RateLimitProperties props = appProperties.getRateLimit();
        if (!props.isEnabled()) return;
        AppProperties.Limit limit = limitFor(action);
        long now = clock.millis();
        long windowMs = limit.getWindow().toMillis();
        Deque<Long> hits = windows.computeIfAbsent(key(userId, action), k -> new ArrayDeque<>());
        synchronized (hits) {
            evict(hits, now - windowMs);
            if (hits.size() >= limit.getRequests()) {
                long retryAfter = Math.max(1, Duration.ofMillis(hits.peekFirst() + windowMs - now).toSeconds());
                log.info("Rate limit reached for user {} ({})", userId, action);
                throw new RateLimitException("You have reached the limit of " + limit.getRequests() + " " + action.noun
                        + " per " + describe(limit.getWindow()) + ". Try again in " + describe(Duration.ofSeconds(retryAfter)) + ".",
                        retryAfter);
            }
            hits.addLast(now);
        }
    }

    /** Current usage without consuming anything. */
    public Usage usage(Long userId, Action action) {
        AppProperties.Limit limit = limitFor(action);
        Deque<Long> hits = windows.get(key(userId, action));
        int used = 0;
        if (hits != null) {
            synchronized (hits) {
                evict(hits, clock.millis() - limit.getWindow().toMillis());
                used = hits.size();
            }
        }
        int remaining = appProperties.getRateLimit().isEnabled() ? Math.max(0, limit.getRequests() - used) : limit.getRequests();
        return new Usage(limit.getRequests(), remaining, limit.getWindow().toSeconds());
    }

    // drop counters that no longer hold any hit inside their window
    @Scheduled(fixedDelay = 15 * 60 * 1000)
    public void purgeIdle() {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> {
            Deque<Long> hits = entry.getValue();
            synchronized (hits) {
                Long last = hits.peekLast();
                return last == null || now - last > Duration.ofDays(1).toMillis();
            }
        });
    }

    private AppProperties.Limit limitFor(Action action) {
        AppProperties.RateLimitProperties props = appProperties.getRateLimit();
        return switch (action) {
            case CHAT -> props.getChat();
            case SEARCH -> props.getSearch();
            case UPLOAD -> props.getUpload();
        };
    }

    private static void evict(Deque<Long> hits, long cutoff) {
        while (!hits.isEmpty() && hits.peekFirst() <= cutoff) {
            hits.pollFirst();
        }
    }

    private static String key(Long userId, Action action) {
        return userId + ":" + action;
    }

    static String describe(Duration d) {
        if (d.toDays() >= 1 && d.toHoursPart() == 0) return d.toDays() == 1 ? "day" : d.toDays() + " days";
        if (d.toHours() >= 1) return d.toHours() == 1 && d.toMinutesPart() == 0 ? "hour" : d.toHours() + " h " + d.toMinutesPart() + " min";
        if (d.toMinutes() >= 1) return d.toMinutes() + " min";
        return d.toSeconds() + " s";
    }
}
