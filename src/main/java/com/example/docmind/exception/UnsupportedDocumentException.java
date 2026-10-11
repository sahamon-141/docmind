package com.example.docmind.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedDocumentException extends ApiException {
    public UnsupportedDocumentException(String message) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_DOCUMENT", message);
    }
}
