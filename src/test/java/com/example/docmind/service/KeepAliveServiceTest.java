package com.example.docmind.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeepAliveServiceTest {

    @Test
    void appendsHealthPathToABaseUrl() {
        assertThat(KeepAliveService.healthUrl("https://docmind-api.onrender.com"))
                .isEqualTo("https://docmind-api.onrender.com/actuator/health");
        assertThat(KeepAliveService.healthUrl(" https://docmind-api.onrender.com/ "))
                .isEqualTo("https://docmind-api.onrender.com/actuator/health");
    }

    @Test
    void keepsAnExplicitPath() {
        assertThat(KeepAliveService.healthUrl("https://docmind-ecru.vercel.app/actuator/health"))
                .isEqualTo("https://docmind-ecru.vercel.app/actuator/health");
    }
}
