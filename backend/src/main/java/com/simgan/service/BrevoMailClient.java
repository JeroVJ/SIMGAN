package com.simgan.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Sends transactional emails through the Brevo HTTP API (https, port 443).
 *
 * Railway blocks outbound SMTP ports (25/465/587), so raw SMTP to Gmail times
 * out. Brevo's REST endpoint travels over HTTPS, which is never blocked, so it
 * works from any PaaS. This client is a no-op when BREVO_API_KEY is unset,
 * letting {@link EmailAlertService} fall back to SMTP for local development.
 */
@Component
@Slf4j
public class BrevoMailClient {

    private static final String API_URL = "https://api.brevo.com/v3/smtp/email";

    private final String apiKey;
    private final RestClient restClient = RestClient.create();

    public BrevoMailClient(@Value("${brevo.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    /** True when an API key is configured and emails can actually be sent. */
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    /**
     * Sends one HTML email. Returns true on HTTP 2xx, false on any failure
     * (errors are logged, never thrown, so callers in @Async threads are safe).
     */
    public boolean send(String fromName, String fromEmail, String to, String subject, String htmlContent) {
        if (!isConfigured()) {
            return false;
        }

        Map<String, Object> sender = StringUtils.hasText(fromName)
                ? Map.of("name", fromName, "email", fromEmail)
                : Map.of("email", fromEmail);

        Map<String, Object> body = Map.of(
                "sender", sender,
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "htmlContent", htmlContent
        );

        try {
            restClient.post()
                    .uri(API_URL)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Brevo email sent to {} subject='{}'", to, subject);
            return true;
        } catch (Exception e) {
            log.error("Brevo email to {} failed: {}", to, e.getMessage());
            return false;
        }
    }
}
