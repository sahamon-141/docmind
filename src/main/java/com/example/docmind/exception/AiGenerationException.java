package com.example.docmind.exception;

import org.springframework.http.HttpStatus;

// The upstream model or embedding call failed. "data" may carry the persisted exchange so the client can retry.
public class AiGenerationException extends ApiException {
    public AiGenerationException(String message, Object data, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "AI_GENERATION_FAILED", message, data, cause);
    }
}
