package com.example.docmind.dto;

import com.example.docmind.service.RateLimiterService;

// What the current user may still do; lets the UI explain limits before a request is rejected.
public record UsageLimitsDto(
        int maxDocuments,
        long documentsUsed,
        long maxFileSizeBytes,
        RateLimiterService.Usage chat,
        RateLimiterService.Usage search,
        RateLimiterService.Usage upload
) {
}
