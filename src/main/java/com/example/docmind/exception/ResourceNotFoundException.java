package com.example.docmind.exception;

import org.springframework.http.HttpStatus;

// Also used for resources owned by another user, so their existence is never revealed.
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException() {
        this("Resources you are looking for not found");
    }

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
