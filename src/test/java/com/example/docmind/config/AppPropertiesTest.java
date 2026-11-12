package com.example.docmind.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

    @Test
    void allowedOriginsAreTrimmedAndLoseTrailingSlashes() {
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties();
        cors.setAllowedOrigins(" https://docmind.vercel.app/ ,http://localhost:5173");
        assertThat(cors.allowedOriginList()).containsExactly("https://docmind.vercel.app", "http://localhost:5173");
    }
}
