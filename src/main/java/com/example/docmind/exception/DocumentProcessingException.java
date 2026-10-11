package com.example.docmind.exception;

import org.springframework.http.HttpStatus;

public class DocumentProcessingException extends ApiException {
    public DocumentProcessingException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "DOCUMENT_PROCESSING_FAILED", message);
    }

    public DocumentProcessingException(String message, Object data, Throwable ex) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "DOCUMENT_PROCESSING_FAILED", message, data, ex);
    }
}
