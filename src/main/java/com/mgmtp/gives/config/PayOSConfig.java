package com.mgmtp.gives.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Slf4j
@Configuration
public class PayOSConfig {

    @Value("${payos.client-id}")
    private String clientId;

    @Value("${payos.api-key}")
    private String apiKey;

    @Value("${payos.checksum-key}")
    private String checksumKey;

    @Value("${payos.webhook-url:}")
    private String webhookUrl;

    @Bean
    public PayOS payOS() {
        return new PayOS(clientId, apiKey, checksumKey);
    }

    @Bean
    public ApplicationRunner payOSWebhookRegistrar(PayOS payOS) {
        return args -> {
            if (webhookUrl != null && !webhookUrl.isBlank()) {
                try {
                    log.info("Attempting to automatically confirm PayOS Webhook URL: {}", webhookUrl);
                    payOS.webhooks().confirm(webhookUrl);
                    log.info("Successfully confirmed PayOS Webhook URL with PayOS backend.");
                } catch (Exception e) {
                    log.warn("Failed to automatically confirm PayOS Webhook URL (expected if running locally without public domain): {}", e.getMessage());
                }
            } else {
                log.info("No payos.webhook-url configured. Skipping automatic webhook confirmation.");
            }
        };
    }
}
