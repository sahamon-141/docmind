package com.example.docmind.service;

import com.example.docmind.config.AppProperties;
import com.example.docmind.entity.RefreshToken;
import com.example.docmind.entity.User;
import com.example.docmind.exception.ConflictException;
import com.example.docmind.exception.InvalidRefreshTokenException;
import com.example.docmind.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final AppProperties properties = new AppProperties();
    private final RefreshTokenService service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    private RefreshToken token(Instant revokedAt, String reason, Instant expiresAt) {
        return RefreshToken.builder().id(UUID.randomUUID()).user(User.builder().id(7L).build())
                .tokenHash(RefreshTokenService.hash("raw")).familyId(UUID.randomUUID())
                .createdAt(NOW.minus(Duration.ofHours(1))).expiresAt(expiresAt)
                .revokedAt(revokedAt).revokedReason(reason).build();
    }

    @Test
    void rotationRevokesPresentedTokenAndIssuesNewOneInSameFamily() {
        RefreshToken current = token(null, null, NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(current));

        RefreshTokenService.Rotation rotation = service.rotate("raw");

        assertThat(rotation.rawToken()).isNotEqualTo("raw").hasSizeGreaterThanOrEqualTo(43);
        assertThat(current.getRevokedReason()).isEqualTo(RefreshToken.REASON_ROTATED);
        assertThat(current.getReplacedById()).isNotNull();
        verify(repository).save(argThat(t -> t.getFamilyId().equals(current.getFamilyId())
                && t.getTokenHash().equals(RefreshTokenService.hash(rotation.rawToken()))));
    }

    @Test
    void concurrentRefreshWithinGraceWindowIsAConflictNotTheft() {
        RefreshToken rotated = token(NOW.minusSeconds(2), RefreshToken.REASON_ROTATED, NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(rotated));

        assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(ConflictException.class);
        verify(repository, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void reuseAfterGraceWindowRevokesWholeFamily() {
        RefreshToken rotated = token(NOW.minusSeconds(60), RefreshToken.REASON_ROTATED, NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(rotated));

        assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revokeFamily(eq(rotated.getFamilyId()), eq(RefreshToken.REASON_REUSE_DETECTED), eq(NOW));
    }

    @Test
    void expiredAndUnknownTokensAreRejected() {
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(token(null, null, NOW.minusSeconds(1))));
        assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(InvalidRefreshTokenException.class);

        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rotate("unknown")).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.rotate(" ")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void cleanupDeletesRowsOlderThanRetention() {
        when(repository.deleteExpiredOrRevokedBefore(any())).thenReturn(3);
        assertThat(service.purgeExpired()).isEqualTo(3);
        verify(repository).deleteExpiredOrRevokedBefore(NOW.minus(properties.getSecurity().getRefresh().getCleanupRetention()));
    }
}
