package com.example.docmind.security;

import com.example.docmind.config.AppProperties;
import com.example.docmind.entity.Role;
import com.example.docmind.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-with-at-least-thirty-two-bytes!";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static AppProperties props(String secret, boolean ephemeral) {
        AppProperties p = new AppProperties();
        p.getSecurity().getJwt().setSecret(secret);
        p.getSecurity().getJwt().setAllowEphemeralSecret(ephemeral);
        return p;
    }

    private static User user() {
        return User.builder().id(42L).username("ada").role(Role.USER).build();
    }

    @Test
    void validTokenRoundTripsToUserId() {
        JwtService jwt = new JwtService(props(SECRET, false), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(jwt.validateAccessToken(jwt.generateAccessToken(user()))).isEqualTo(42L);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService issuer = new JwtService(props(SECRET, false), Clock.fixed(NOW, ZoneOffset.UTC));
        JwtService later = new JwtService(props(SECRET, false), Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));
        String token = issuer.generateAccessToken(user());
        assertThatThrownBy(() -> later.validateAccessToken(token))
                .isInstanceOf(TokenValidationException.class)
                .extracting("code").isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService a = new JwtService(props(SECRET, false), Clock.systemUTC());
        JwtService b = new JwtService(props(SECRET.replace('u', 'x'), false), Clock.systemUTC());
        assertThatThrownBy(() -> b.validateAccessToken(a.generateAccessToken(user())))
                .isInstanceOf(TokenValidationException.class)
                .extracting("code").isEqualTo("INVALID_TOKEN");
    }

    @Test
    void tokenWithoutAccessPurposeIsRejected() {
        JwtService jwt = new JwtService(props(SECRET, false), Clock.systemUTC());
        String noType = Jwts.builder().issuer("docmind").subject("42")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThatThrownBy(() -> jwt.validateAccessToken(noType)).isInstanceOf(TokenValidationException.class);
    }

    @Test
    void missingOrShortSecretFailsStartupUnlessEphemeralAllowed() {
        assertThatThrownBy(() -> new JwtService(props("", false), Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(props("too-short", false), Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        JwtService dev = new JwtService(props("", true), Clock.systemUTC());
        assertThat(dev.validateAccessToken(dev.generateAccessToken(user()))).isEqualTo(42L);
    }
}
