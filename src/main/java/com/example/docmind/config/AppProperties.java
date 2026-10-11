package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Configuration
@ConfigurationProperties("app")
@Getter
@Setter
public class AppProperties {
    private RagProperties rag = new RagProperties(); //make sure the name for object is same as in the yaml file
    private DocumentProperties documents = new DocumentProperties();
    private CorsProperties cors = new CorsProperties();
    private SecurityProperties security = new SecurityProperties();

    @Getter
    @Setter
    public static class CorsProperties {
        // exact origins only; "*" is rejected because the refresh-token cookie needs credentialed requests
        private String allowedOrigins = "";
        private String allowedMethods = "GET,POST,PATCH,PUT,DELETE,OPTIONS";
        private String allowedHeaders = "Authorization,Content-Type,X-DocMind-CSRF,Accept";

        public List<String> allowedOriginList() {
            return splitCsv(allowedOrigins);
        }
    }

    @Getter
    @Setter
    public static class RagProperties {
        private int chunkSize = 700;
        private int minChunkSizeChars = 350;
        private int minChunkLengthToEmbed = 5;
        private int maxNumChunks = 10000;
        private int topK = 5; // represents top k similar chunks to be chosen
        private int maxTopK = 20; // upper bound for client supplied topK
        private double similarityThreshold = 0.0; // 0.0 means don't reject based on the similarity score
        private int snippetLength = 600; // characters of chunk text returned in citations
        private int historyMessages = 6; // previous messages sent to the model for follow-up questions
    }

    @Getter
    @Setter
    public static class DocumentProperties {
        private int maxFilesPerRequest = 10;
        private String allowedExtensions = "pdf,txt,md,markdown,csv,docx,doc,pptx,odt,rtf,html,htm";

        public List<String> allowedExtensionList() {
            return splitCsv(allowedExtensions).stream().map(e -> e.toLowerCase(Locale.ROOT)).toList();
        }
    }

    @Getter
    @Setter
    public static class SecurityProperties {
        private JwtProperties jwt = new JwtProperties();
        private RefreshProperties refresh = new RefreshProperties();
    }

    @Getter
    @Setter
    public static class JwtProperties {
        private String secret = "";
        // only the dev profile enables this; production refuses to start without JWT_SECRET
        private boolean allowEphemeralSecret = false;
        private String issuer = "docmind";
        private Duration accessTokenTtl = Duration.ofMinutes(15);
    }

    @Getter
    @Setter
    public static class RefreshProperties {
        private Duration ttl = Duration.ofDays(7);
        private String cookieName = "docmind_refresh";
        private String cookiePath = "/api/v1/auth";
        private boolean cookieSecure = true;
        private String cookieSameSite = "Strict";
        // a rotated token presented again within this window is treated as a concurrent refresh, not theft
        private Duration reuseGracePeriod = Duration.ofSeconds(10);
        private String cleanupCron = "0 15 3 * * *";
        // revoked/expired rows are kept this long so reuse can still be detected
        private Duration cleanupRetention = Duration.ofDays(7);
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
