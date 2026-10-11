package com.example.docmind.controller;

import com.example.docmind.config.AppProperties;
import com.example.docmind.dto.ApiResponse;
import com.example.docmind.dto.AuthResponse;
import com.example.docmind.dto.LoginRequest;
import com.example.docmind.dto.RegisterUserRequest;
import com.example.docmind.dto.UserDto;
import com.example.docmind.security.AuthUser;
import com.example.docmind.security.CookieCsrfGuard;
import com.example.docmind.service.AuthService;
import com.example.docmind.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = """
        Short-lived JWT access tokens (send as `Authorization: Bearer <token>`) plus a rotating, opaque refresh
        token stored in an HttpOnly cookie scoped to /api/v1/auth. Refresh and logout additionally require the
        `X-DocMind-CSRF: 1` header.""")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final CookieCsrfGuard csrfGuard;
    private final AppProperties appProperties;

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Create an account and sign in", description = "Returns an access token and sets the refresh-token cookie. 409 if the username or email is taken.")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterUserRequest request) {
        AuthService.AuthResult result = authService.register(request);
        return withRefreshCookie(HttpStatus.CREATED, result, "Account created");
    }

    //authenticate and return the token and user
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Sign in with username (or email) and password", description = "401 INVALID_CREDENTIALS on failure.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResult result = authService.login(request);
        return withRefreshCookie(HttpStatus.OK, result, "Signed in");
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Rotate the refresh-token cookie and get a new access token",
            description = "401 INVALID_REFRESH_TOKEN when missing/expired/revoked (reuse of a rotated token revokes the session). "
                    + "409 REFRESH_CONFLICT when another request refreshed the same session a moment ago; retry once.",
            parameters = @Parameter(in = ParameterIn.HEADER, name = CookieCsrfGuard.HEADER, required = true, example = "1"))
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(HttpServletRequest request) {
        csrfGuard.verify(request);
        AuthService.AuthResult result = authService.refresh(readRefreshCookie(request));
        return withRefreshCookie(HttpStatus.OK, result, "Session refreshed");
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Revoke the current session's refresh tokens and clear the cookie",
            parameters = @Parameter(in = ParameterIn.HEADER, name = CookieCsrfGuard.HEADER, required = true, example = "1"))
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        csrfGuard.verify(request);
        authService.logout(readRefreshCookie(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .body(ApiResponse.ok(null, "Signed out"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's profile")
    public ResponseEntity<ApiResponse<UserDto>> me(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(UserDto.from(userService.getById(user.id())), null));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(HttpStatus status, AuthService.AuthResult result, String message) {
        ResponseCookie cookie = refreshCookie(result.refreshToken(), appProperties.getSecurity().getRefresh().getTtl());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.ok(result.response(), message));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        AppProperties.RefreshProperties refresh = appProperties.getSecurity().getRefresh();
        return ResponseCookie.from(refresh.getCookieName(), value)
                .httpOnly(true)
                .secure(refresh.isCookieSecure())
                .sameSite(refresh.getCookieSameSite())
                .path(refresh.getCookiePath())
                .maxAge(maxAge)
                .build();
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        String name = appProperties.getSecurity().getRefresh().getCookieName();
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
