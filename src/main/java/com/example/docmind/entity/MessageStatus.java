package com.example.docmind.entity;

public enum MessageStatus {
    COMPLETE,
    // assistant generation failed; the user message is kept and can be retried
    FAILED
}
