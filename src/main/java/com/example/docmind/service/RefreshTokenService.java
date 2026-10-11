package com.example.docmind.service;

import com.example.docmind.config.AppProperties;
import com.example.docmind.entity.RefreshToken;
import com.example.docmind.entity.User;
import com.example.docmind.exception.ConflictException;
import com.example.docmind.exception.InvalidRefreshTokenException;
import com.example.docmind.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens with rotation and reuse detection.
 * <ul>
 *   <li>Tokens are 256 bits from SecureRandom; only their SHA-256 hash is stored.</li>
 *   <li>Every refresh revokes the presented token and issues a new one in the same family.</li>
 *   <li>Presenting an already-rotated token outside the grace window revokes the whole family.</li>
 *   <li>Concurrent refreshes are serialised by a row lock on the presented token.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final AppProperties appProperties;
    private final Clock clock;

    public record Rotation(User user, String rawToken) {
    }

    @Transactional
    public String issue(User user) {
        return issue(user, UUID.randomUUID()).rawToken();
    }

    private Issued issue(User user, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(hash(raw))
                .familyId(familyId)
                .createdAt(now)
                .expiresAt(now.plus(appProperties.getSecurity().getRefresh().getTtl()))
                .build();
        repository.save(token);
        return new Issued(raw, token);
    }

    // Family revocation on reuse must survive the 401 that follows, hence noRollbackFor.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        RefreshToken current = repository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();

        if (current.isRevoked()) {
            boolean recentlyRotated = RefreshToken.REASON_ROTATED.equals(current.getRevokedReason())
                    && current.getRevokedAt().plus(appProperties.getSecurity().getRefresh().getReuseGracePeriod()).isAfter(now);
            if (recentlyRotated) {
                // Another tab or request won the race. Its new cookie is already in the browser, so the client can retry.
                throw new ConflictException("REFRESH_CONFLICT", "Session was refreshed concurrently; retry the request");
            }
            int revoked = repository.revokeFamily(current.getFamilyId(), RefreshToken.REASON_REUSE_DETECTED, now);
            log.warn("Refresh token reuse detected for user {}; revoked {} token(s) in family", current.getUser().getId(), revoked);
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        Issued next = issue(current.getUser(), current.getFamilyId());
        current.revoke(RefreshToken.REASON_ROTATED, now);
        current.setReplacedById(next.token().getId());
        return new Rotation(current.getUser(), next.rawToken());
    }

    // Logout: revoke every token of this login session (the family), whatever its state.
    @Transactional
    public void revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), RefreshToken.REASON_LOGOUT, clock.instant()));
    }

    @Transactional
    public void revokeAllForUser(Long userId, String reason) {
        repository.revokeAllForUser(userId, reason, clock.instant());
    }

    @Scheduled(cron = "${app.security.refresh.cleanup-cron}")
    @Transactional
    public int purgeExpired() {
        Instant cutoff = clock.instant().minus(appProperties.getSecurity().getRefresh().getCleanupRetention());
        int deleted = repository.deleteExpiredOrRevokedBefore(cutoff);
        if (deleted > 0) log.info("Purged {} expired or revoked refresh tokens", deleted);
        return deleted;
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record Issued(String rawToken, RefreshToken token) {
    }
}
