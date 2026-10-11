package com.example.docmind.security;

import com.example.docmind.config.AppProperties;
import com.example.docmind.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * CSRF protection for the two endpoints authenticated by the refresh-token cookie (refresh, logout).
 * <p>
 * The rest of the API uses bearer tokens, which browsers never attach automatically, so Spring's
 * synchronizer-token CSRF filter is disabled. For the cookie endpoints we layer three defences:
 * <ol>
 *   <li>SameSite on the cookie (Strict by default),</li>
 *   <li>a mandatory custom header, which a cross-site form cannot send and a cross-origin script can only
 *       send after a CORS preflight that our exact-origin allowlist rejects,</li>
 *   <li>an Origin check: when the browser sends Origin it must be same-host or an allowed CORS origin.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class CookieCsrfGuard {
    public static final String HEADER = "X-DocMind-CSRF";

    private final AppProperties appProperties;

    public void verify(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            throw forbidden();
        }
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return; // same-origin non-CORS requests from some browsers omit Origin; the custom header still applies
        }
        if (appProperties.getCors().allowedOriginList().contains(origin)) {
            return;
        }
        try {
            String host = URI.create(origin).getHost();
            if (host != null && host.equalsIgnoreCase(request.getServerName())) {
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // malformed Origin falls through to rejection
        }
        throw forbidden();
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "CSRF_REJECTED", "Request rejected by CSRF protection");
    }
}
