package com.example.docmind.service;

import com.example.docmind.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Render's free plan spins an instance down after about 15 minutes without inbound traffic. This job
 * calls the service's own public health URL (which goes out through Render's proxy and counts as
 * traffic) every few minutes. It only runs when app.keep-alive.url is set, which on Render happens
 * automatically through RENDER_EXTERNAL_URL. It cannot wake an instance that is already asleep;
 * the GitHub Actions workflow in .github/workflows/keep-alive.yml covers that case.
 */
@Service
@ConditionalOnExpression("!'${app.keep-alive.url:}'.isBlank()")
public class KeepAliveService {
    private static final Logger log = LoggerFactory.getLogger(KeepAliveService.class);
    static final String HEALTH_PATH = "/actuator/health";

    private final String url;
    private final RestClient restClient;

    public KeepAliveService(AppProperties appProperties) {
        this.url = healthUrl(appProperties.getKeepAlive().getUrl());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        log.info("Keep-alive enabled: pinging {} every {}", url, appProperties.getKeepAlive().getInterval());
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "${app.keep-alive.interval:10m}")
    public void ping() {
        try {
            restClient.get().uri(url).retrieve().toBodilessEntity();
            log.debug("Keep-alive ping to {} succeeded", url);
        } catch (RuntimeException e) {
            log.warn("Keep-alive ping to {} failed: {}", url, e.getMessage());
        }
    }

    // "https://x.onrender.com" -> "https://x.onrender.com/actuator/health"; a URL with a path is kept as is
    static String healthUrl(String configured) {
        String trimmed = configured.trim().replaceAll("/+$", "");
        boolean hasPath = trimmed.replaceFirst("^https?://", "").contains("/");
        return hasPath ? trimmed : trimmed + HEALTH_PATH;
    }
}
