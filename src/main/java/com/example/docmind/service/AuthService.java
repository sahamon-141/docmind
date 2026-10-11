package com.example.docmind.service;

import com.example.docmind.dto.AuthResponse;
import com.example.docmind.dto.LoginRequest;
import com.example.docmind.dto.RegisterUserRequest;
import com.example.docmind.dto.UserDto;
import com.example.docmind.entity.User;
import com.example.docmind.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    // Body for the client plus the raw refresh token, which the controller puts in an HttpOnly cookie.
    public record AuthResult(AuthResponse response, String refreshToken) {
    }

    @Transactional
    public AuthResult register(RegisterUserRequest request) {
        User user = userService.registerUser(request);
        log.info("Registered user {}", user.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        // throws BadCredentialsException (401) for unknown users and wrong passwords alike
        Authentication authenticated = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        User user = ((CustomUserDetail) authenticated.getPrincipal()).getUser();
        log.info("User {} logged in", user.getId());
        return issueTokens(user);
    }

    // Not transactional itself: rotate() commits (including a reuse-triggered family revocation) on its own.
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        User user = rotation.user();
        return new AuthResult(
                AuthResponse.bearer(jwtService.generateAccessToken(user), jwtService.getAccessTokenTtlSeconds(), UserDto.from(user)),
                rotation.rawToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeSession(rawRefreshToken);
    }

    private AuthResult issueTokens(User user) {
        String refresh = refreshTokenService.issue(user);
        String access = jwtService.generateAccessToken(user);
        return new AuthResult(AuthResponse.bearer(access, jwtService.getAccessTokenTtlSeconds(), UserDto.from(user)), refresh);
    }
}
