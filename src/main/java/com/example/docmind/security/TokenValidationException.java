package com.example.docmind.security;

import lombok.Getter;
import org.springframework.security.core.AuthenticationException;

@Getter
public class TokenValidationException extends AuthenticationException {
    private final String code;

    public TokenValidationException(String code, String message) {
        super(message);
        this.code = code;
    }
}
