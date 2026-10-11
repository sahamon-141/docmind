package com.example.docmind.dto;

// Returned by login, register and refresh. The refresh token is never in the body: it is set as an HttpOnly cookie.
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserDto user
) {
    public static AuthResponse bearer(String accessToken, long expiresInSeconds, UserDto user) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, user);
    }
}
