package com.example.docmind.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// Base class for errors whose message is safe to show to the client.
@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final transient Object data;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null, null);
    }

    public ApiException(HttpStatus status, String code, String message, Object data, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.data = data;
    }
}
