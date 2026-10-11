package com.example.docmind.security;

import com.example.docmind.config.AppProperties;
import com.example.docmind.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

// Issues and validates short-lived HS256 access tokens. Refresh tokens are opaque and handled by RefreshTokenService.
@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    static final String CLAIM_TYPE = "typ";
    static final String TYPE_ACCESS = "access";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        AppProperties.JwtProperties jwt = properties.getSecurity().getJwt();
        this.signingKey = Keys.hmacShaKeyFor(resolveSecret(jwt));
        this.issuer = jwt.getIssuer();
        this.accessTokenTtl = jwt.getAccessTokenTtl();
        this.clock = clock;
    }

    private static byte[] resolveSecret(AppProperties.JwtProperties jwt) {
        String secret = jwt.getSecret();
        if (secret == null || secret.isBlank()) {
            if (!jwt.isAllowEphemeralSecret()) {
                throw new IllegalStateException("JWT_SECRET must be set (at least " + MIN_SECRET_BYTES + " bytes)");
            }
            log.warn("JWT_SECRET is not set; using a random per-process signing key (development only)");
            byte[] random = new byte[64];
            new SecureRandom().nextBytes(random);
            return random;
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        return bytes;
    }

    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    // Returns the user id of a valid access token. Signature, issuer, expiry and token purpose are all checked.
    public Long validateAccessToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenValidationException("TOKEN_EXPIRED", "Access token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenValidationException("INVALID_TOKEN", "Access token is invalid");
        }
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new TokenValidationException("INVALID_TOKEN", "Token is not an access token");
        }
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new TokenValidationException("INVALID_TOKEN", "Access token is invalid");
        }
    }
}
