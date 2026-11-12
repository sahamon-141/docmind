package com.example.docmind.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// 429 with a Retry-After header (added by GlobalExceptionHandler).
@Getter
public class RateLimitException extends ApiException {
    private final long retryAfterSeconds;

    public RateLimitException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
